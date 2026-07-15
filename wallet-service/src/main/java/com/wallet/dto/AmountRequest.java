package com.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AmountRequest(
    /**
     * Сумма. Строго > 0.
     * Digits: до 15 знаков в целой части, до 4 в дробной.
     * Соответствует DECIMAL(19,4) в БД.
     */
    @NotNull
    @DecimalMin(value = "0.0001", message = "amount must be positive")
    @Digits(integer = 15, fraction = 4)
    BigDecimal amount
) {}
