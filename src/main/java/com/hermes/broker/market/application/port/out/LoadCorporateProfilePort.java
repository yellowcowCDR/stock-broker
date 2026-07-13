package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.CorporateProfile;
import java.util.Optional;

public interface LoadCorporateProfilePort {
    Optional<CorporateProfile> loadProfile(String corpCode);
}
