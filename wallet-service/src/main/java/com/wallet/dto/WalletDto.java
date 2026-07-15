package com.wallet.dto;

import com.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletDto(
    UUID id,
    UUID userId,
    String currency,
    BigDecimal balance,
    Instant createdAt
) {
    public static WalletDto from(Wallet wallet) {
        return new WalletDto(
            wallet.getId(),
            wallet.getUser().getId(),
            wallet.getCurrency(),
            wallet.getBalance(),
            wallet.getCreatedAt()
        );
    }
}
