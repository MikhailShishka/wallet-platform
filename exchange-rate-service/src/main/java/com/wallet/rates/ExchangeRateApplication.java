package com.wallet.rates;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Полностью реактивный микросервис курсов валют.
 *
 * Стек:
 * - Spring WebFlux (Netty event-loop, non-blocking).
 * - Reactive Mongo Driver (Flux/Mono, не блокирует).
 * - Reactor Sinks для broadcast обновлений подписчикам SSE.
 *
 * Отличие от wallet-service:
 * - Возвращаем Mono<T> / Flux<T>, а не T.
 * - НЕТ JPA/JDBC — только реактивные драйверы.
 * - НЕТ Tomcat — Netty на 8082.
 */
@SpringBootApplication
@EnableScheduling
public class ExchangeRateApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExchangeRateApplication.class, args);
    }
}
