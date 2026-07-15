package com.wallet.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT-фильтр уровня Gateway.
 *
 * Порядок работы:
 * 1. Проверяем, является ли путь публичным (регистрация, логин) — пропускаем.
 * 2. Извлекаем Authorization: Bearer <token>.
 * 3. Валидируем токен через JJWT (тот же секрет, что в wallet-service).
 * 4. Извлекаем userId из sub.
 * 5. Прокидываем X-User-Id в upstream — сервисам не нужно парсить JWT заново.
 *
 * ⚠️ Defence in depth: wallet-service ВСЁ РАВНО проверяет JWT.
 * X-User-Id — только для удобства, не заменяет проверку.
 * Если бы мы полагались на X-User-Id, злоумышленник в internal-сети
 * мог бы отправить любой userId в заголовке и получить доступ к чужим данным.
 *
 * Это GlobalFilter — применяется ко ВСЕМ маршрутам автоматически.
 * Ordered.HIGHEST_PRECEDENCE + 1 — почти первый в цепочке, чтобы отсечь неавторизованные.
 */
@Component
@Slf4j
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private final String secret;
    private final List<String> publicPaths;
    private SecretKey signingKey;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthGatewayFilter(
            @Value("${jwt.secret}") String secret,
            @Value("${security.public-paths}") List<String> publicPaths
    ) {
        this.secret = secret;
        this.publicPaths = publicPaths;
    }

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Публичные пути — пропускаем без проверки
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing Authorization header");
        }

        String token = authHeader.substring("Bearer ".length());
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String userId = claims.getSubject();
            log.debug("Authenticated userId={} for path {}", userId, path);

            // Прокидываем X-User-Id для upstream — удобство, не защита
            ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", userId)
                .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (JwtException e) {
            return unauthorized(exchange, "Invalid token: " + e.getMessage());
        }
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.debug("401: {}", reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    /**
     * Максимально высокий приоритет: до маршрутизации.
     * -1 — раньше стандартных фильтров, но после RemoteAddrResolver.
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
