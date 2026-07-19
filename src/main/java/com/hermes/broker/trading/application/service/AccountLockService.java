package com.hermes.broker.trading.application.service;

import java.util.function.Supplier;

public interface AccountLockService {
    <T> T executeWithLock(String accountKey, Supplier<T> action);
}
