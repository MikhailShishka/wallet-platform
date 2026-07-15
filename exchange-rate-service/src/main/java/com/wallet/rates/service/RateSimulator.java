package com.wallet.rates.service;

import com.wallet.rates.config.RatesProperties;
import com.wallet.rates.entity.Rate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Симулятор колебаний курсов.
 *
 * При старте:
 * - Засеиваем в БД стартовые курсы из конфига (если их ещё нет).
 * - Держим in-memory состояние текущего курса.
 *
 * По расписанию:
 * - Каждые N мс делаем случайное отклонение на ±volatility%.
 * - Сохраняем через RateService (в БД + broadcast подписчикам SSE).
 *
 * Примечание про реактивщину:
 * subscribe() вызываем ЯВНО, потому что @Scheduled — это императивный
 * триггер. Без subscribe() Flux/Mono останутся ленивыми и ничего не сделают.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateSimulator {

    private final RatesProperties properties;
    private final RateService rateService;

    // In-memory state текущих курсов (для быстрого пересчёта).
    // ConcurrentHashMap: доступ из @PostConstruct и @Scheduled потока.
    private final Map<String, BigDecimal> currentRates = new HashMap<>();

    @PostConstruct
    void seed() {
        // Засеиваем стартовые курсы: если в БД по паре ещё нет, кладём seed-значение.
        Flux.fromIterable(properties.getPairs())
            .flatMap(pair -> {
                BigDecimal seedValue = properties.getSeed().get(pair);
                if (seedValue == null) {
                    log.warn("No seed value for pair {}", pair);
                    return reactor.core.publisher.Mono.empty();
                }
                currentRates.put(pair, seedValue);
                return rateService.getCurrentRate(pair)
                    .switchIfEmpty(rateService.saveRate(Rate.builder()
                        .pair(pair)
                        .rate(seedValue)
                        .occurredAt(Instant.now())
                        .build()))
                    .doOnNext(r -> currentRates.put(r.getPair(), r.getRate()));
            })
            .subscribe(
                r -> log.info("Seeded pair {}: current rate = {}", r.getPair(), r.getRate()),
                err -> log.error("Seed failed", err)
            );
    }

    @Scheduled(fixedDelayString = "${rates.simulator.interval-ms}")
    public void tick() {
        double volatility = properties.getSimulator().getVolatility();

        Flux.fromIterable(properties.getPairs())
            .flatMap(pair -> {
                BigDecimal current = currentRates.get(pair);
                if (current == null) return reactor.core.publisher.Mono.empty();

                // Случайное отклонение [-volatility, +volatility]
                double shift = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * volatility;
                BigDecimal newRate = current
                    .multiply(BigDecimal.valueOf(1 + shift))
                    .setScale(6, RoundingMode.HALF_UP);

                currentRates.put(pair, newRate);

                return rateService.saveRate(Rate.builder()
                    .pair(pair)
                    .rate(newRate)
                    .occurredAt(Instant.now())
                    .build());
            })
            .subscribe(
                r -> {},   // логирование внутри saveRate
                err -> log.error("Simulator tick failed", err)
            );
    }
}
