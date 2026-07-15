package com.wallet.history.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Запись истории операций в MongoDB.
 *
 * eventId — уникальный (@Indexed unique) → механизм идемпотентности.
 * Повторное событие с тем же eventId → duplicate key → игнорируем.
 *
 * walletId индексирован для быстрой выборки истории конкретного кошелька.
 */
@Document(collection = "transaction_history")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionHistoryEntry {

    @Id
    private String id;                    // MongoDB ObjectId

    @Indexed(unique = true)
    private UUID eventId;                 // защита от дублей = идемпотентность

    @Indexed
    private UUID walletId;

    private String type;                  // DEPOSIT / WITHDRAW / TRANSFER
    private UUID counterpartyWalletId;    // для TRANSFER
    private UUID transferGroupId;         // для TRANSFER
    private BigDecimal amount;
    private String currency;
    private Instant occurredAt;
    private Instant receivedAt;           // когда history-service обработал
}
