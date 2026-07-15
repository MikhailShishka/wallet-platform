package com.wallet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "wallets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_wallets_user_currency",
        columnNames = {"user_id", "currency"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * LAZY всегда — даже для @ManyToOne.
     * Eager-дефолт = неконтролируемые JOIN'ы и N+1.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** ISO 4217: RUB / USD / EUR. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Деньги — только BigDecimal. Никаких float/double. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * Оптимистическая блокировка.
     * Hibernate сам инкрементирует и проверяет.
     */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
