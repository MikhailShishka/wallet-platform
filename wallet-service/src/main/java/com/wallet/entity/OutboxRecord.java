package com.wallet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox pattern.
 * Запись создаётся в ОДНОЙ транзакции с бизнес-изменением (баланс, ledger).
 * Отдельный OutboxRelay периодически читает pending → отправляет в Kafka → отмечает sent.
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * JSON-строка события. Тип колонки в БД — JSONB.
     * @JdbcTypeCode(SqlTypes.JSON) — Hibernate 6+ умеет автоматически конвертить String ↔ JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private Integer attempts = 0;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /** Хелпер: пометить как отправленное. */
    public void markSent() {
        this.sentAt = Instant.now();
    }
}
