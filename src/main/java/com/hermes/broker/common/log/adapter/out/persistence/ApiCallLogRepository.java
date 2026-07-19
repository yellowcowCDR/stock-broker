package com.hermes.broker.common.log.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ApiCallLogRepository extends JpaRepository<ApiCallLogJpaEntity, Long> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApiCallLogJpaEntity a WHERE a.createdAt < :createdAt")
    int deleteByCreatedAtBefore(@Param("createdAt") Instant createdAt);
}
