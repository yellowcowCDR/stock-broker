package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.domain.UsFundamentalsSnapshot;

public interface GetUsFundamentalsUseCase {
    UsFundamentalsSnapshot getUsFundamentals(String symbol);
}
