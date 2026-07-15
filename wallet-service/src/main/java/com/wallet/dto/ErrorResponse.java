package com.wallet.dto;

import java.time.Instant;

/**
 * Единый формат ошибки для всех API-ответов.
 * Возвращается из GlobalExceptionHandler.
 */
public record ErrorResponse(
    String code,
    String message,
    Instant timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }
}
