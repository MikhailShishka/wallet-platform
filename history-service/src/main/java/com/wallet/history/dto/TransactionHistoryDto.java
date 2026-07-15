package com.wallet.history.dto;

import com.wallet.history.entity.TransactionHistoryEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryDto(
    UUID eventId,
    String type,
    UUID walletId,
    UUID counterpartyWalletId,
    UUID transferGroupId,
    BigDecimal amount,
    String currency,
    Instant occurredAt
) {
    public static TransactionHistoryDto from(TransactionHistoryEntry e) {
        return new TransactionHistoryDto(
            e.getEventId(),
            e.getType(),
            e.getWalletId(),
            e.getCounterpartyWalletId(),
            e.getTransferGroupId(),
            e.getAmount(),
            e.getCurrency(),
            e.getOccurredAt()
        );
    }
}
