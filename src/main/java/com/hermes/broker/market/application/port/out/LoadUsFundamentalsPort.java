package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.UsFundamentalsSnapshot;

public interface LoadUsFundamentalsPort {
    UsFundamentalsSnapshot load(String symbol);
}
