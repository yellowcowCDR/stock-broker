package com.hermes.broker.common.monitoring.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerRuntimeStateJpaRepository
        extends JpaRepository<BrokerRuntimeStateJpaEntity, String> {
}
