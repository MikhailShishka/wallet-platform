package com.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Событие «деньги были переведены/списаны/зачислены».
 *
 * Один и тот же тип покрывает DEPOSIT / WITHDRAW / TRANSFER —
 * поле type различает. Для transfer записываются ДВА события с одним
 * transferGroupId (списание и зачисление).
 *
 * ⚠️ eventId — уникальный идентификатор именно СОБЫТИЯ (не транзакции).
 * По нему consumer делает idempotency check в MongoDB.
 * Он совпадает с id записи Transaction, что удобно для трассировки.
 *
 * Публикуется через OutboxRelay в топик money-transfers.
 * Ключ Kafka-сообщения = walletId — гарантирует порядок событий
 * одного кошелька (все попадают в одну партицию).
 */
public record MoneyTransferredEvent(
    UUID eventId,
    String type,                 // DEPOSIT / WITHDRAW / TRANSFER
    UUID walletId,
    UUID counterpartyWalletId,   // NULL для DEPOSIT/WITHDRAW, вторая сторона перевода
    UUID transferGroupId,        // NULL для DEPOSIT/WITHDRAW, общий для пары строк transfer
    BigDecimal amount,
    String currency,
    Instant occurredAt
) {}
