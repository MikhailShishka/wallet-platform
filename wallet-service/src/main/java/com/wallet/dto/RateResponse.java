package com.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ответ от exchange-rate-service: /api/v1/rates/{pair}.
 * Формат совпадает с RateDto в exchange-rate-service — это контракт.
 */
public record RateResponse(
    String pair,
    BigDecimal rate,
    Instant occurredAt
) {}
