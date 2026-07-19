package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.service.AccountLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class PostgresAccountLockService implements AccountLockService {

    private final DataSource dataSource;

    @Override
    public <T> T executeWithLock(String accountKey, Supplier<T> action) {
        long lockId = UUID.nameUUIDFromBytes(accountKey.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        try (Connection connection = dataSource.getConnection()) {
            acquire(connection, lockId);
            try {
                return action.get();
            } finally {
                release(connection, lockId);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to acquire account execution lock", e);
        }
    }

    private void acquire(Connection connection, long lockId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_lock(?)")) {
            statement.setLong(1, lockId);
            statement.execute();
        }
    }

    private void release(Connection connection, long lockId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, lockId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || !resultSet.getBoolean(1)) {
                    throw new IllegalStateException("Account execution lock was not held by this session");
                }
            }
        }
    }
}
