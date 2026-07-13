package com.hermes.broker.market.domain;

import java.time.LocalDate;

public record CorporateDisclosure(
        String receiptNumber,
        String corpName,
        String reportName,
        String submitterName,
        LocalDate receiptDate,
        String remarks
) {
}
