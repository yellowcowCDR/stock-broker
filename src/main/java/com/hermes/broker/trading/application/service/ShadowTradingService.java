package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.market.dto.response.MarketStatusResponseDto;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.ManageShadowTradingUseCase;
import com.hermes.broker.trading.application.port.in.ShadowDecisionResult;
import com.hermes.broker.trading.application.port.in.StartShadowDecisionCommand;
import com.hermes.broker.trading.application.port.out.LoadShadowPerformanceSamplePort;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.application.port.out.SaveShadowPerformanceSamplePort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OverseasExchange;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShadowTradingService implements ManageShadowTradingUseCase {

    private static final String QUOTE_SOURCE = "KIS_QUOTE_API";

    private final CreateTradingDecisionUseCase createTradingDecisionUseCase;
    private final LoadTradingDecisionPort loadTradingDecisionPort;
    private final LoadTradingFeaturePort loadTradingFeaturePort;
    private final LoadShadowPerformanceSamplePort loadShadowPerformanceSamplePort;
    private final SaveShadowPerformanceSamplePort saveShadowPerformanceSamplePort;
    private final List<MarketTradingPort> marketTradingPorts;
    private final MarketTimeValidator marketTimeValidator;
    private final TradingTimeService tradingTimeService;
    private final Clock clock;

    @Override
    @Transactional
    public ShadowDecisionResult start(StartShadowDecisionCommand command) {
        if (command == null || command.decision() == null) {
            throw new IllegalArgumentException("decision is required.");
        }
        if (command.decision().idempotencyKey() == null
                || command.decision().idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("decision.idempotencyKey is required.");
        }

        var existingDecision = loadTradingDecisionPort
                .loadByIdempotencyKey(command.decision().idempotencyKey());
        TradingDecision decision = createTradingDecisionUseCase
                .createShadowDecision(command.decision());
        var existingSample = loadShadowPerformanceSamplePort
                .loadByDecisionId(decision.decisionId());
        if (existingSample.isPresent()) {
            return new ShadowDecisionResult(decision, existingSample.get(), true);
        }
        if (existingDecision.isPresent()) {
            throw new IllegalStateException("Shadow decision " + decision.decisionId()
                    + " exists without its quote sample; manual data repair is required.");
        }

        TradingFeatureSnapshot feature = loadTradingFeaturePort.loadById(decision.featureId())
                .orElseThrow(() -> new IllegalStateException(
                        "Feature " + decision.featureId() + " no longer exists."));
        marketTimeValidator.validateMarketOpen(feature.marketType().name());
        String exchangeCode = normalizeExchange(feature.marketType(), command.exchangeCode());
        BigDecimal referencePrice = currentPrice(feature, exchangeCode);
        var sample = new ShadowPerformanceSample(
                UUID.randomUUID().toString(), decision.decisionId(), decision.featureId(),
                decision.strategyVersion(), feature.stockCode(), feature.marketType(), exchangeCode,
                decision.decisionType(), referencePrice, null, null, null,
                tradingTimeService.currentMarketDate(feature.marketType()),
                ShadowSampleStatus.PENDING, QUOTE_SOURCE, clock.instant(), null);
        return new ShadowDecisionResult(
                decision, saveShadowPerformanceSamplePort.save(sample), false);
    }

    @Override
    @Transactional
    public List<ShadowPerformanceSample> settle(MarketType marketType, LocalDate tradingDate) {
        if (marketType == null || tradingDate == null) {
            throw new IllegalArgumentException("marketType and tradingDate are required.");
        }
        LocalDate currentMarketDate = tradingTimeService.currentMarketDate(marketType);
        if (!tradingDate.equals(currentMarketDate)) {
            throw new IllegalArgumentException("Shadow settlement only supports the current "
                    + marketType + " market date because historical closing quotes are not used."
                    + " requested=" + tradingDate + ", current=" + currentMarketDate);
        }
        requireClosedSession(marketType);

        List<ShadowPerformanceSample> pending = loadShadowPerformanceSamplePort
                .loadByMarketAndTradingDateAndStatus(
                        marketType, tradingDate, ShadowSampleStatus.PENDING);
        if (pending.isEmpty()) {
            throw new DataPipelineUnavailableException("No pending real-quote shadow samples exist for "
                    + marketType + " on " + tradingDate + ".");
        }
        return pending.stream().map(this::settleOne).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShadowPerformanceSample> getSamples(
            String strategyVersion, ShadowSampleStatus status) {
        if (strategyVersion == null || strategyVersion.isBlank()) {
            throw new IllegalArgumentException("strategyVersion is required.");
        }
        return loadShadowPerformanceSamplePort.loadByStrategyVersion(
                strategyVersion.trim(), status == null ? ShadowSampleStatus.COMPLETED : status);
    }

    private ShadowPerformanceSample settleOne(ShadowPerformanceSample sample) {
        TradingFeatureSnapshot feature = loadTradingFeaturePort.loadById(sample.featureId())
                .orElseThrow(() -> new DataPipelineUnavailableException(
                        "Feature " + sample.featureId() + " is missing for shadow sample "
                                + sample.sampleId() + "."));
        BigDecimal observedPrice = currentPrice(feature, sample.exchangeCode());
        BigDecimal rawReturn = observedPrice.subtract(sample.referencePrice())
                .divide(sample.referencePrice(), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal actionReturn = switch (sample.decisionType()) {
            case BUY -> rawReturn;
            case SELL -> rawReturn.negate();
            case HOLD, BLOCK -> BigDecimal.ZERO.setScale(6, RoundingMode.UNNECESSARY);
        };
        ShadowPerformanceSample completed = new ShadowPerformanceSample(
                sample.sampleId(), sample.decisionId(), sample.featureId(),
                sample.strategyVersion(), sample.stockCode(), sample.marketType(),
                sample.exchangeCode(), sample.decisionType(), sample.referencePrice(),
                observedPrice, rawReturn, actionReturn, sample.tradingDate(),
                ShadowSampleStatus.COMPLETED, sample.dataSource(), sample.startedAt(),
                clock.instant());
        return saveShadowPerformanceSamplePort.save(completed);
    }

    private void requireClosedSession(MarketType marketType) {
        MarketStatusResponseDto status = marketTimeValidator.getMarketStatus(marketType.name());
        if (!status.isComplete()) {
            throw new DataPipelineUnavailableException("Market calendar is incomplete for "
                    + marketType + ": " + status.getReason());
        }
        if (status.isOpen() || status.getSessionClosesAt() == null
                || status.getCheckedAt() == null
                || status.getCheckedAt().isBefore(status.getSessionClosesAt())) {
            throw new IllegalStateException("Shadow samples can be settled only after the regular "
                    + marketType + " session closes.");
        }
    }

    private BigDecimal currentPrice(TradingFeatureSnapshot feature, String exchangeCode) {
        MarketTradingPort marketPort = marketTradingPorts.stream()
                .filter(port -> port.supports(feature.marketType()))
                .findFirst()
                .orElseThrow(() -> new DataPipelineUnavailableException(
                        "No quote adapter supports " + feature.marketType() + "."));
        CurrentPriceDto quote = feature.marketType() == MarketType.OVERSEAS
                ? marketPort.getCurrentPrice(feature.stockCode(), exchangeCode)
                : marketPort.getCurrentPrice(feature.stockCode());
        if (quote == null || quote.getCurrentPrice() == null
                || quote.getCurrentPrice().signum() <= 0) {
            throw new DataPipelineUnavailableException("A positive Broker quote is unavailable for "
                    + feature.stockCode() + ".");
        }
        return quote.getCurrentPrice();
    }

    private String normalizeExchange(MarketType marketType, String exchangeCode) {
        return marketType == MarketType.OVERSEAS
                ? OverseasExchange.from(exchangeCode).orderExchangeCode()
                : null;
    }
}
