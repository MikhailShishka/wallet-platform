package com.wallet.exception;

import java.util.UUID;

public class WalletAlreadyExistsException extends RuntimeException {
    public WalletAlreadyExistsException(UUID userId, String currency) {
        super("Wallet already exists for user " + userId + " and currency " + currency);
    }
}
