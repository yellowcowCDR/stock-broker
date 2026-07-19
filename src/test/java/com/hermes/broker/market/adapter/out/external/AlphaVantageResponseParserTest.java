package com.hermes.broker.market.adapter.out.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.market.domain.UsFinancialReport;
import com.hermes.broker.market.domain.UsFundamentalsSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaVantageResponseParserTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AlphaVantageResponseParser parser = new AlphaVantageResponseParser();

    @Test
    void parsesFinancialsButDoesNotInventUpcomingAnnouncementTime() throws Exception {
        String calendar = """
                symbol,name,reportDate,fiscalDateEnding,estimate,currency
                AAPL,"Apple, Inc.",2026-07-30,2026-06-30,1.45,USD
                """;

        UsFundamentalsSnapshot snapshot = parse(calendar);

        assertThat(snapshot.symbol()).isEqualTo("AAPL");
        assertThat(snapshot.financialReports())
                .extracting(UsFinancialReport::statementType)
                .contains("INCOME_STATEMENT", "BALANCE_SHEET", "CASH_FLOW");
        assertThat(snapshot.upcomingEarnings().name()).isEqualTo("Apple, Inc.");
        assertThat(snapshot.upcomingEarnings().announcementTimeUtc()).isNull();
        assertThat(snapshot.upcomingEarnings().announcementTimePrecision()).isEqualTo("DATE_ONLY");
        assertThat(snapshot.financialDataComplete()).isTrue();
        assertThat(snapshot.earningsCalendarComplete()).isTrue();
        assertThat(snapshot.announcementTimeComplete()).isFalse();
        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.warnings()).isNotEmpty();
        assertThat(snapshot.validUntil()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void noScheduledEventDoesNotRequireAnInventedTimestamp() throws Exception {
        String calendar = "symbol,name,reportDate,fiscalDateEnding,estimate,currency\n";

        UsFundamentalsSnapshot snapshot = parse(calendar);

        assertThat(snapshot.upcomingEarnings()).isNull();
        assertThat(snapshot.announcementTimeComplete()).isTrue();
        assertThat(snapshot.complete()).isTrue();
    }

    @Test
    void providerRateLimitResponseFailsClosed() throws Exception {
        JsonNode rateLimit = objectMapper.readTree("{\"Note\":\"API call frequency exceeded\"}");

        assertThatThrownBy(() -> parser.parse(
                "AAPL", overview(), rateLimit, statement(), statement(), earnings(),
                "symbol,name,reportDate,fiscalDateEnding,estimate,currency\n",
                NOW, Duration.ofHours(24)))
                .isInstanceOf(DataPipelineUnavailableException.class)
                .hasMessageContaining("frequency exceeded");
    }

    private UsFundamentalsSnapshot parse(String calendar) throws Exception {
        return parser.parse(
                "AAPL",
                overview(),
                statement(),
                statement(),
                statement(),
                earnings(),
                calendar,
                NOW,
                Duration.ofHours(24)
        );
    }

    private JsonNode overview() throws Exception {
        return objectMapper.readTree("""
                {
                  "Symbol": "AAPL",
                  "AssetType": "Common Stock",
                  "Name": "Apple Inc",
                  "Exchange": "NASDAQ",
                  "Currency": "USD",
                  "Country": "USA",
                  "Sector": "TECHNOLOGY",
                  "LatestQuarter": "2026-06-30",
                  "MarketCapitalization": "3000000000000"
                }
                """);
    }

    private JsonNode statement() throws Exception {
        return objectMapper.readTree("""
                {
                  "symbol": "AAPL",
                  "quarterlyReports": [{
                    "fiscalDateEnding": "2026-06-30",
                    "reportedCurrency": "USD",
                    "totalRevenue": "100000000000",
                    "netIncome": "25000000000"
                  }],
                  "annualReports": [{
                    "fiscalDateEnding": "2025-09-30",
                    "reportedCurrency": "USD",
                    "totalRevenue": "390000000000",
                    "netIncome": "95000000000"
                  }]
                }
                """);
    }

    private JsonNode earnings() throws Exception {
        return objectMapper.readTree("""
                {
                  "symbol": "AAPL",
                  "quarterlyEarnings": [{
                    "fiscalDateEnding": "2026-03-31",
                    "reportedDate": "2026-05-01",
                    "reportedEPS": "1.50",
                    "estimatedEPS": "1.42",
                    "surprise": "0.08",
                    "surprisePercentage": "5.63",
                    "reportTime": "post-market"
                  }]
                }
                """);
    }
}
