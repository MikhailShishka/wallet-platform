package com.wallet.dto;

import java.util.UUID;

public record LoginResponse(
    String token,
    UUID userId,
    String tokenType   // всегда "Bearer", для удобства клиента
) {
    public static LoginResponse of(String token, UUID userId) {
        return new LoginResponse(token, userId, "Bearer");
    }
}
