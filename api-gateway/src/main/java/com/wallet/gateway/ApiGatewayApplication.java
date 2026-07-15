package com.wallet.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway для wallet-platform.
 *
 * Единая точка входа для клиентов. Возможности:
 * - Маршрутизация: /api/v1/wallets/** → wallet-service, /api/v1/rates/** → exchange-rate-service.
 * - Аутентификация: проверка JWT и добавление X-User-Id для upstream-сервисов.
 * - Логирование: X-Request-Id добавляется в каждый запрос.
 * - CORS: единая политика для всех сервисов.
 *
 * Работает на Netty (WebFlux) — non-blocking, тысячи параллельных соединений.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
