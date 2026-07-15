package com.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateWalletRequest(
    @NotNull UUID userId,

    /**
     * ISO 4217: ровно 3 буквы в верхнем регистре.
     * Pattern строже, чем @Size — отсекает 'rub' и '123'.
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be 3 uppercase letters (ISO 4217)")
    String currency
) {}
