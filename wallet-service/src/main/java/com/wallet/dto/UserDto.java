package com.wallet.dto;

import com.wallet.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Ответ API про пользователя.
 * Важно: НЕ содержит passwordHash — отделение Entity от DTO
 * как раз для защиты таких полей.
 */
public record UserDto(
    UUID id,
    String email,
    String firstName,
    String lastName,
    Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getCreatedAt()
        );
    }
}
