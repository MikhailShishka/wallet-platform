package com.wallet.rates.dto;

import com.wallet.rates.entity.Rate;

import java.math.BigDecimal;
import java.time.Instant;

public record RateDto(
    String pair,
    BigDecimal rate,
    Instant occurredAt
) {
    public static RateDto from(Rate r) {
        return new RateDto(r.getPair(), r.getRate(), r.getOccurredAt());
    }
}
