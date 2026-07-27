package com.hermes.broker.common.monitoring.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CronHeartbeatJpaRepository extends JpaRepository<CronHeartbeatJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select heartbeat from CronHeartbeatJpaEntity heartbeat "
            + "where heartbeat.cronName = :cronName")
    Optional<CronHeartbeatJpaEntity> findByCronNameForUpdate(
            @Param("cronName") String cronName);
}
