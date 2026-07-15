package com.wallet.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Генерация и валидация JWT.
 * Используется HMAC-SHA256: один секрет на сервере, без асимметрии.
 * Для микросервисов с разными командами лучше RS256 (асимметричная подпись).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs
    ) {
        // Минимум 32 байта для HS256 — иначе JJWT бросит исключение
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Создать access-токен для пользователя.
     * sub = userId (стандартный claim), email — кастомный.
     */
    public String generateToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Распарсить токен и проверить подпись + срок.
     * Бросает JwtException (или его наследников) при невалидном/истёкшем токене.
     */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseAndValidate(token).getSubject());
    }
}
