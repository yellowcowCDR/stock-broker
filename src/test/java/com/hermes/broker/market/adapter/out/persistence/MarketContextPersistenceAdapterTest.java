package com.hermes.broker.market.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketContextPersistenceAdapterTest {

    @Test
    void roundTripsUtcOverviewSnapshotWithoutLosingTypes() {
        MarketContextJpaRepository repository = mock(MarketContextJpaRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MarketContextPersistenceAdapter adapter = new MarketContextPersistenceAdapter(repository, objectMapper);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant now = Instant.parse("2026-07-20T01:00:00Z");
        MarketOverview overview = new MarketOverview(
                MarketType.DOMESTIC, List.of(), 600, 300, 100, new BigDecimal("0.3"),
                new BigDecimal("120000"), new BigDecimal("-200000"), new BigDecimal("80000"),
                "KIS_API_NATIVE", "KIS_OPEN_API:TEST", now, now.plusSeconds(300),
                true, "FRESH");
        MarketContext context = new MarketContext(
                "context-1", MarketType.DOMESTIC, MarketEntryPolicy.REDUCE_NEW_ENTRIES,
                new BigDecimal("0.5"), overview, List.of("mixed breadth"), "hermes",
                "correlation-1", now, now.plusSeconds(300));

        MarketContext result = adapter.save(context);

        assertThat(result.overviewSnapshot().marketType()).isEqualTo(MarketType.DOMESTIC);
        assertThat(result.overviewSnapshot().fetchedAt()).isEqualTo(now);
        assertThat(result.overviewSnapshot().breadthScore()).isEqualByComparingTo("0.3");
        assertThat(result.validUntil()).isEqualTo(now.plusSeconds(300));
    }
}
