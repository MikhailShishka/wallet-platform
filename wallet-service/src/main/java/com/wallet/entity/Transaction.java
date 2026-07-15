package com.wallet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Транзакция — append-only ledger.
 *
 * ⚠️ Никаких UPDATE. Исправление ошибочной операции — обратная транзакция,
 * а не изменение существующей строки.
 *
 * Нет @Version — при append-only не нужна оптимистическая блокировка
 * (нет одновременных UPDATE-ов одной строки).
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Связывает пару строк одного перевода. NULL для DEPOSIT/WITHDRAW. */
    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    /** Уникальный ключ идемпотентности. UNIQUE constraint в БД. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
