package com.hermes.broker.common.monitoring.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "broker_runtime_state")
@Getter
@NoArgsConstructor
public class BrokerRuntimeStateJpaEntity {

    @Id
    @Column(name = "state_key", length = 100)
    private String stateKey;

    @Column(name = "state_value", nullable = false, length = 500)
    private String stateValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BrokerRuntimeStateJpaEntity(String stateKey, String stateValue, Instant updatedAt) {
        this.stateKey = stateKey;
        this.stateValue = stateValue;
        this.updatedAt = updatedAt;
    }

    public void update(String stateValue, Instant updatedAt) {
        this.stateValue = stateValue;
        this.updatedAt = updatedAt;
    }
}
