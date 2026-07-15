package com.wallet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient — non-blocking HTTP-клиент от Spring.
 * Используется даже в MVC-приложении, потому что через Resilience4j
 * мы работаем с CompletableFuture / Mono.
 *
 * Base URL берётся из wallet.exchange-rate.base-url.
 */
@Configuration
public class ExchangeRateClientConfig {

    @Bean
    public WebClient exchangeRateWebClient(
            @Value("${wallet.exchange-rate.base-url}") String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }
}
