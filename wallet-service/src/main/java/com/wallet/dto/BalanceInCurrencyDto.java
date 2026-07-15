package com.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Баланс кошелька, конвертированный в целевую валюту.
 * rateOccurredAt — время фиксации курса.
 * stale=true → курс из fallback-кэша, downstream был недоступен.
 */
public record BalanceInCurrencyDto(
    UUID walletId,
    String walletCurrency,
    BigDecimal walletBalance,
    String targetCurrency,
    BigDecimal convertedBalance,
    BigDecimal rate,
    Instant rateOccurredAt,
    boolean stale
) {}
