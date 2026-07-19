package com.hermes.broker.market.application.service;

import com.hermes.broker.common.property.MarketContextProperties;
import com.hermes.broker.market.application.port.in.CreateMarketContextCommand;
import com.hermes.broker.market.application.port.in.GetMarketOverviewUseCase;
import com.hermes.broker.market.application.port.in.MarketContextUseCase;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.application.port.out.SaveMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketContextService implements MarketContextUseCase {

    private final GetMarketOverviewUseCase getMarketOverviewUseCase;
    private final SaveMarketContextPort saveMarketContextPort;
    private final LoadMarketContextPort loadMarketContextPort;
    private final MarketContextProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public MarketContext create(CreateMarketContextCommand command) {
        validateCommand(command);
        Instant now = clock.instant();
        MarketOverview overview = getMarketOverviewUseCase.getOverview(command.marketType());
        if (overview.marketType() != command.marketType()) {
            throw new IllegalStateException("Market overview does not match the requested market.");
        }

        Duration maxValidity = properties.maxValidity() == null
                ? Duration.ofMinutes(5) : properties.maxValidity();
        Instant latestAllowed = earlierOf(overview.validUntil(), now.plus(maxValidity));
        Instant validUntil = command.validUntil() == null ? latestAllowed : command.validUntil();
        if (!validUntil.isAfter(now)) {
            throw new IllegalArgumentException("Market context validUntil must be in the future.");
        }
        if (validUntil.isAfter(latestAllowed)) {
            throw new IllegalArgumentException(
                    "Market context validity cannot exceed the underlying overview freshness window.");
        }

        List<String> rationale = command.rationale().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (rationale.isEmpty()) {
            throw new IllegalArgumentException("At least one non-blank rationale is required.");
        }

        MarketContext context = new MarketContext(
                UUID.randomUUID().toString(),
                command.marketType(),
                command.entryPolicy(),
                command.riskMultiplier(),
                overview,
                rationale,
                command.analyzedBy().trim(),
                command.correlationId() == null || command.correlationId().isBlank()
                        ? UUID.randomUUID().toString() : command.correlationId().trim(),
                now,
                validUntil
        );
        return saveMarketContextPort.save(context);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketContext> getLatest(MarketType marketType) {
        if (marketType == null) {
            throw new IllegalArgumentException("marketType is required.");
        }
        return loadMarketContextPort.loadLatest(marketType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketContext> getHistory(MarketType marketType) {
        if (marketType == null) {
            throw new IllegalArgumentException("marketType is required.");
        }
        int limit = properties.historyLimit() > 0 ? properties.historyLimit() : 100;
        return loadMarketContextPort.loadHistory(marketType, limit);
    }

    private void validateCommand(CreateMarketContextCommand command) {
        if (command == null || command.marketType() == null || command.entryPolicy() == null
                || command.riskMultiplier() == null || command.analyzedBy() == null
                || command.analyzedBy().isBlank() || command.rationale() == null) {
            throw new IllegalArgumentException(
                    "marketType, entryPolicy, riskMultiplier, analyzedBy and rationale are required.");
        }
        BigDecimal multiplier = command.riskMultiplier();
        if (multiplier.compareTo(BigDecimal.ZERO) < 0 || multiplier.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("riskMultiplier must be between 0 and 1.");
        }
        if (command.entryPolicy() == MarketEntryPolicy.BLOCK_NEW_ENTRIES
                && multiplier.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("BLOCK_NEW_ENTRIES requires riskMultiplier 0.");
        }
        if (command.entryPolicy() == MarketEntryPolicy.ALLOW_NEW_ENTRIES
                && multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("ALLOW_NEW_ENTRIES requires a positive riskMultiplier.");
        }
        if (command.entryPolicy() == MarketEntryPolicy.REDUCE_NEW_ENTRIES
                && (multiplier.compareTo(BigDecimal.ZERO) <= 0
                || multiplier.compareTo(BigDecimal.ONE) >= 0)) {
            throw new IllegalArgumentException(
                    "REDUCE_NEW_ENTRIES requires riskMultiplier greater than 0 and less than 1.");
        }
    }

    private Instant earlierOf(Instant first, Instant second) {
        if (first == null) {
            throw new IllegalStateException("Market overview has no validity boundary.");
        }
        return first.isBefore(second) ? first : second;
    }
}
