package com.hermes.broker.market.application.port.out;

import java.util.Optional;

public interface LoadDartCorporationPort {
    Optional<String> getCorpCode(String stockCode);
}
