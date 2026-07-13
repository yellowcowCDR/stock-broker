package com.hermes.broker.market.domain;

public record CorporateProfile(
        String corpName,
        String corpNameEng,
        String stockName,
        String stockCode,
        String ceoName,
        String corpClass,
        String setupDate,
        String settlementMonth
) {
}
