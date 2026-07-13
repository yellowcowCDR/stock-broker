package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.CorporateDisclosure;
import java.util.List;

public interface LoadCorporateDisclosurePort {
    List<CorporateDisclosure> loadRecentDisclosures(String corpCode);
}
