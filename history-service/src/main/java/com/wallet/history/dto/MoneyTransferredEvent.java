package com.wallet.history.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Событие «деньги были переведены».
 * Контракт с wallet-service, поля должны совпадать байт-в-байт.
 *
 * Публикатор — wallet-service.OutboxRelay.
 * Топик — money-transfers.
 * Ключ Kafka = walletId → одна партиция для всех событий кошелька.
 */
public record MoneyTransferredEvent(
    UUID eventId,
    String type,                 // DEPOSIT / WITHDRAW / TRANSFER
    UUID walletId,
    UUID counterpartyWalletId,
    UUID transferGroupId,
    BigDecimal amount,
    String currency,
    Instant occurredAt
) {}
