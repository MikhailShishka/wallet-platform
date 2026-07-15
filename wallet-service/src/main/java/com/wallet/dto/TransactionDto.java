package com.wallet.dto;

import com.wallet.entity.Transaction;
import com.wallet.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionDto(
    UUID id,
    UUID walletId,
    TransactionType type,
    BigDecimal amount,
    UUID transferGroupId,
    Instant createdAt
) {
    public static TransactionDto from(Transaction tx) {
        return new TransactionDto(
            tx.getId(),
            tx.getWallet().getId(),
            tx.getType(),
            tx.getAmount(),
            tx.getTransferGroupId(),
            tx.getCreatedAt()
        );
    }
}
