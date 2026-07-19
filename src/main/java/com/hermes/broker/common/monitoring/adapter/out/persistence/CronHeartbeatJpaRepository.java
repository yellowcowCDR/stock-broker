package com.hermes.broker.common.monitoring.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CronHeartbeatJpaRepository extends JpaRepository<CronHeartbeatJpaEntity, String> {
}
