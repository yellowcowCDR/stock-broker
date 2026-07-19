package com.hermes.broker.market.adapter.out.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.market.domain.UsEarningsSchedule;
import com.hermes.broker.market.domain.UsFinancialReport;
import com.hermes.broker.market.domain.UsFundamentalsSnapshot;
import com.hermes.broker.market.domain.UsHistoricalEarnings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AlphaVantageResponseParser {

    private static final int MAX_QUARTERLY_REPORTS = 8;
    private static final int MAX_ANNUAL_REPORTS = 3;
    private static final int MAX_EARNINGS_HISTORY = 12;

    public UsFundamentalsSnapshot parse(
            String expectedSymbol,
            JsonNode overview,
            JsonNode incomeStatement,
            JsonNode balanceSheet,
            JsonNode cashFlow,
            JsonNode earnings,
            String earningsCalendarCsv,
            Instant fetchedAt,
            Duration freshnessThreshold
    ) {
        requireProviderSuccess(overview, "OVERVIEW");
        requireProviderSuccess(incomeStatement, "INCOME_STATEMENT");
        requireProviderSuccess(balanceSheet, "BALANCE_SHEET");
        requireProviderSuccess(cashFlow, "CASH_FLOW");
        requireProviderSuccess(earnings, "EARNINGS");

        Map<String, String> companyOverview = flatTextMap(overview);
        String actualSymbol = companyOverview.getOrDefault("Symbol", "");
        if (!expectedSymbol.equalsIgnoreCase(actualSymbol)) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage OVERVIEW symbol mismatch for " + expectedSymbol + "."
            );
        }

        List<UsFinancialReport> reports = new ArrayList<>();
        reports.addAll(parseStatements(incomeStatement, "INCOME_STATEMENT"));
        reports.addAll(parseStatements(balanceSheet, "BALANCE_SHEET"));
        reports.addAll(parseStatements(cashFlow, "CASH_FLOW"));
        if (reports.stream().map(UsFinancialReport::statementType).distinct().count() != 3) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage did not return all three financial statement types for "
                            + expectedSymbol + "."
            );
        }

        List<UsHistoricalEarnings> earningsHistory = parseEarningsHistory(earnings);
        if (earningsHistory.isEmpty()) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage EARNINGS history is empty for " + expectedSymbol + "."
            );
        }

        CalendarResult calendar = parseCalendar(expectedSymbol, earningsCalendarCsv);
        List<String> warnings = new ArrayList<>();
        boolean announcementTimeComplete = calendar.upcoming() == null
                || calendar.upcoming().announcementTimeUtc() != null;
        if (!announcementTimeComplete) {
            warnings.add(
                    "Upcoming earnings date is available, but the provider did not supply an exact UTC announcement time."
            );
        }

        boolean complete = calendar.complete() && announcementTimeComplete;
        return new UsFundamentalsSnapshot(
                expectedSymbol,
                companyOverview,
                reports,
                earningsHistory,
                calendar.upcoming(),
                List.of(
                        "ALPHA_VANTAGE:OVERVIEW",
                        "ALPHA_VANTAGE:INCOME_STATEMENT",
                        "ALPHA_VANTAGE:BALANCE_SHEET",
                        "ALPHA_VANTAGE:CASH_FLOW",
                        "ALPHA_VANTAGE:EARNINGS",
                        "ALPHA_VANTAGE:EARNINGS_CALENDAR"
                ),
                fetchedAt,
                fetchedAt.plus(freshnessThreshold),
                true,
                calendar.complete(),
                announcementTimeComplete,
                complete,
                warnings
        );
    }

    private List<UsFinancialReport> parseStatements(JsonNode root, String statementType) {
        List<UsFinancialReport> result = new ArrayList<>();
        appendReports(result, root.path("quarterlyReports"), statementType, "QUARTERLY", MAX_QUARTERLY_REPORTS);
        appendReports(result, root.path("annualReports"), statementType, "ANNUAL", MAX_ANNUAL_REPORTS);
        return result;
    }

    private void appendReports(
            List<UsFinancialReport> target,
            JsonNode rows,
            String statementType,
            String periodType,
            int limit
    ) {
        if (!rows.isArray()) {
            return;
        }
        int added = 0;
        for (JsonNode row : rows) {
            if (added >= limit) {
                break;
            }
            Map<String, String> values = flatTextMap(row);
            LocalDate fiscalDate = requiredDate(values.remove("fiscalDateEnding"), "fiscalDateEnding");
            String currency = requiredText(values.remove("reportedCurrency"), "reportedCurrency");
            target.add(new UsFinancialReport(
                    statementType,
                    periodType,
                    fiscalDate,
                    currency,
                    values
            ));
            added++;
        }
    }

    private List<UsHistoricalEarnings> parseEarningsHistory(JsonNode root) {
        JsonNode rows = root.path("quarterlyEarnings");
        if (!rows.isArray()) {
            return List.of();
        }
        List<UsHistoricalEarnings> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (result.size() >= MAX_EARNINGS_HISTORY) {
                break;
            }
            result.add(new UsHistoricalEarnings(
                    requiredDate(row.path("fiscalDateEnding").asText(""), "earnings fiscalDateEnding"),
                    requiredDate(row.path("reportedDate").asText(""), "earnings reportedDate"),
                    providerValue(row, "reportedEPS"),
                    providerValue(row, "estimatedEPS"),
                    providerValue(row, "surprise"),
                    providerValue(row, "surprisePercentage"),
                    providerValue(row, "reportTime")
            ));
        }
        return result;
    }

    private CalendarResult parseCalendar(String symbol, String csv) {
        if (csv == null || csv.isBlank()) {
            throw new DataPipelineUnavailableException("Alpha Vantage EARNINGS_CALENDAR response is empty.");
        }
        String trimmed = csv.trim();
        if (trimmed.startsWith("{")) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage EARNINGS_CALENDAR returned an error or rate-limit response."
            );
        }

        List<List<String>> rows = parseCsv(trimmed);
        if (rows.isEmpty()) {
            throw new DataPipelineUnavailableException("Alpha Vantage EARNINGS_CALENDAR has no header.");
        }
        Map<String, Integer> header = headerIndex(rows.get(0));
        requireHeader(header, "symbol");
        requireHeader(header, "reportDate");
        requireHeader(header, "fiscalDateEnding");
        requireHeader(header, "currency");

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (!symbol.equalsIgnoreCase(value(row, header, "symbol"))) {
                continue;
            }
            UsEarningsSchedule schedule = new UsEarningsSchedule(
                    symbol,
                    value(row, header, "name"),
                    requiredDate(value(row, header, "reportDate"), "calendar reportDate"),
                    requiredDate(value(row, header, "fiscalDateEnding"), "calendar fiscalDateEnding"),
                    value(row, header, "estimate"),
                    requiredText(value(row, header, "currency"), "calendar currency"),
                    parseExactInstant(value(row, header, "announcementTimeUtc")),
                    header.containsKey("announcementTimeUtc")
                            && !value(row, header, "announcementTimeUtc").isBlank()
                            ? "EXACT_UTC" : "DATE_ONLY",
                    "ALPHA_VANTAGE:EARNINGS_CALENDAR"
            );
            return new CalendarResult(schedule, true);
        }
        return new CalendarResult(null, true);
    }

    private void requireProviderSuccess(JsonNode root, String function) {
        if (root == null || !root.isObject() || root.isEmpty()) {
            throw new DataPipelineUnavailableException("Alpha Vantage " + function + " response is empty.");
        }
        for (String errorField : List.of("Error Message", "Note", "Information")) {
            if (root.hasNonNull(errorField) && !root.path(errorField).asText("").isBlank()) {
                throw new DataPipelineUnavailableException(
                        "Alpha Vantage " + function + " is unavailable: "
                                + root.path(errorField).asText()
                );
            }
        }
    }

    private Map<String, String> flatTextMap(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isValueNode()) {
                values.put(field.getKey(), field.getValue().asText());
            }
        }
        return values;
    }

    private List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < csv.length(); i++) {
            char current = csv.charAt(i);
            if (quoted) {
                if (current == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (current == '"') {
                    quoted = false;
                } else {
                    field.append(current);
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                row.add(field.toString().trim());
                field.setLength(0);
            } else if (current == '\n') {
                row.add(field.toString().trim());
                field.setLength(0);
                if (!row.stream().allMatch(String::isBlank)) {
                    rows.add(List.copyOf(row));
                }
                row.clear();
            } else if (current != '\r') {
                field.append(current);
            }
        }
        row.add(field.toString().trim());
        if (!row.stream().allMatch(String::isBlank)) {
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    private Map<String, Integer> headerIndex(List<String> headerRow) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String header = headerRow.get(i);
            if (i == 0) {
                header = header.replace("\uFEFF", "");
            }
            result.put(header, i);
        }
        return result;
    }

    private void requireHeader(Map<String, Integer> header, String name) {
        if (!header.containsKey(name)) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage EARNINGS_CALENDAR is missing column " + name + "."
            );
        }
    }

    private String value(List<String> row, Map<String, Integer> header, String name) {
        Integer index = header.get(name);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private String providerValue(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return "None".equalsIgnoreCase(value) ? "" : value;
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank() || "None".equalsIgnoreCase(value)) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage response is missing " + field + "."
            );
        }
        return value;
    }

    private LocalDate requiredDate(String value, String field) {
        try {
            return LocalDate.parse(requiredText(value, field));
        } catch (DateTimeParseException invalid) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage response has invalid date " + field + "."
            );
        }
    }

    private Instant parseExactInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage earnings announcement UTC timestamp is invalid."
            );
        }
    }

    private record CalendarResult(UsEarningsSchedule upcoming, boolean complete) {
    }
}
