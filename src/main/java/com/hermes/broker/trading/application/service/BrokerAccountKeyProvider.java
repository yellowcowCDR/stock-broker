package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BrokerAccountKeyProvider {

    private final KisProperties kisProperties;

    public String getAccountKey() {
        String accountNumber = kisProperties.api().accountNo();
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalStateException("Broker account number is not configured.");
        }
        String source = kisProperties.environment().name() + ":" + accountNumber;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
