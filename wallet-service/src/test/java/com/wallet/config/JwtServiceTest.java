package com.wallet.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Unit-тест JwtService — без Spring, без БД, без сети.
 * Просто конструктор с параметрами.
 */
class JwtServiceTest {

    private static final String SECRET =
        "test-secret-must-be-at-least-32-bytes-long-abcdefgh";
    private static final long EXPIRATION_1_HOUR = 3600_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_1_HOUR);
    }

    @Test
    void shouldGenerateAndParseValidToken() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        // WHEN
        String token = jwtService.generateToken(userId, email);
        Claims claims = jwtService.parseAndValidate(token);

        // THEN
        assertThat(token).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void extractUserIdShouldReturnCorrectUuid() {
        // GIVEN
        UUID userId = UUID.randomUUID();

        // WHEN
        String token = jwtService.generateToken(userId, "any@mail.com");

        // THEN
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void shouldRejectTokenWithBadSignature() {
        // GIVEN — сгенерил токен одним секретом, парсим другим
        String token = jwtService.generateToken(UUID.randomUUID(), "x@x.com");

        JwtService other = new JwtService(
            "OTHER-secret-must-be-at-least-32-bytes-lng-xyz",
            EXPIRATION_1_HOUR);

        // WHEN + THEN
        assertThatThrownBy(() -> other.parseAndValidate(token))
            .isInstanceOf(SignatureException.class);
    }

    @Test
    void shouldRejectTamperedToken() {
        // GIVEN
        String token = jwtService.generateToken(UUID.randomUUID(), "x@x.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        // WHEN + THEN
        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
            .isInstanceOf(SignatureException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        // GIVEN — токен с очень коротким сроком
        JwtService shortLived = new JwtService(SECRET, 50L);   // 50 мс
        String token = shortLived.generateToken(UUID.randomUUID(), "x@x.com");

        // WHEN + THEN — ждём протухания
        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThatThrownBy(() -> shortLived.parseAndValidate(token))
                    .isInstanceOf(ExpiredJwtException.class)
            );
    }
}
