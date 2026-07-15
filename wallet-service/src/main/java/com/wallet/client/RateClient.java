package com.wallet.client;

import com.wallet.dto.RateResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиент к exchange-rate-service.
 *
 * ПОРЯДОК АННОТАЦИЙ (верхняя = ВНЕШНЯЯ):
 *   @CircuitBreaker  ← снаружи всех
 *      @Retry        ← ретраит попытки
 *        @TimeLimiter ← ограничивает каждую попытку
 *           метод   ← сам HTTP-вызов
 *
 * Так CircuitBreaker учитывает КОНЕЧНЫЙ результат (успех/неудача всех retry).
 * Если поменять порядок — retry будет сжигать статистику CB на каждой попытке.
 *
 * fallbackMethod вызывается когда:
 *  - CB в OPEN (сразу, ни одного вызова),
 *  - Retry исчерпал попытки,
 *  - TimeLimiter отсёк вызов по времени.
 *
 * Возвращаем CompletableFuture, потому что TimeLimiter работает только с ним.
 * Внутри метода используем WebClient (реактивный), но конвертируем в
 * CompletableFuture через .toFuture().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateClient {

    private static final String CB_NAME = "exchange-rate";

    private final WebClient exchangeRateWebClient;

    /**
     * Локальный stale cache — последнее известное значение по каждой паре.
     * Используется в fallback. ConcurrentHashMap — потокобезопасно.
     * В проде можно вынести в Redis (shared между инстансами).
     */
    private final Map<String, RateResponse> lastKnownRates = new ConcurrentHashMap<>();

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getRateFallback")
    @Retry(name = CB_NAME)
    @TimeLimiter(name = CB_NAME)
    public CompletableFuture<RateResponse> getRate(String pair) {
        log.debug("Calling exchange-rate-service for pair {}", pair);
        return exchangeRateWebClient.get()
            .uri("/api/v1/rates/{pair}", pair)
            .retrieve()
            .bodyToMono(RateResponse.class)
            // Сохраняем в кэш при УСПЕХЕ — для будущих fallback
            .doOnNext(r -> lastKnownRates.put(pair, r))
            .toFuture();
    }

    /**
     * Fallback: сигнатура должна СОВПАДАТЬ с основным методом
     * плюс Throwable в конце.
     *
     * Стратегия:
     * 1. Пытаемся отдать последнее известное значение с пометкой в логах.
     * 2. Если такого нет — бросаем осознанное RateUnavailableException.
     *
     * ВАЖНО: fallback НЕ должен ходить в тот же сервис (иначе смысл теряется).
     */
    public CompletableFuture<RateResponse> getRateFallback(String pair, Throwable ex) {
        log.warn("Fallback for pair {} — {}", pair, ex.getClass().getSimpleName());
        Optional<RateResponse> cached = Optional.ofNullable(lastKnownRates.get(pair));

        if (cached.isPresent()) {
            RateResponse stale = cached.get();
            log.info("Returning stale rate for {}: {} @ {}",
                    pair, stale.rate(), stale.occurredAt());
            return CompletableFuture.completedFuture(stale);
        }

        return CompletableFuture.failedFuture(
            new RateUnavailableException("Rate for " + pair + " unavailable and no cached value", ex)
        );
    }
}
