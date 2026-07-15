package com.wallet.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID walletId, BigDecimal requested, BigDecimal available) {
        super("Insufficient funds in wallet " + walletId
              + ": requested=" + requested + ", available=" + available);
    }
}
