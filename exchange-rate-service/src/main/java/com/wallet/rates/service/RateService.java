package com.wallet.rates.service;

import com.wallet.rates.entity.Rate;
import com.wallet.rates.repository.RateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Реактивная бизнес-логика курсов.
 *
 * Ключевая деталь: используем Sinks.Many для broadcast свежих курсов
 * всем подписчикам SSE. Sinks — Reactor-примитив «Publisher, куда можно
 * толкать значения программно».
 *
 * multicast().onBackpressureBuffer():
 *   - multicast → множество подписчиков получают одни и те же события.
 *   - onBackpressureBuffer → если подписчик медленный, копим в буфер
 *     (для реального прода — settings ограничивать размер).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateService {

    private final RateRepository rateRepository;

    /**
     * Broadcast-канал свежих курсов.
     * saveRate публикует, streamRates подписывает.
     */
    private final Sinks.Many<Rate> updates = Sinks.many().multicast().onBackpressureBuffer();

    /** Актуальный (последний) курс по паре. */
    public Mono<Rate> getCurrentRate(String pair) {
        return rateRepository.findFirstByPairOrderByOccurredAtDesc(pair);
    }

    /**
     * Стрим свежих обновлений курса.
     * Первое значение — текущий из БД, дальше — все новые тики симулятора.
     */
    public Flux<Rate> streamRates(String pair) {
        Mono<Rate> current = getCurrentRate(pair);
        Flux<Rate> future = updates.asFlux()
            .filter(r -> r.getPair().equals(pair));

        // Сначала текущее значение, потом бесконечный поток обновлений
        return Flux.concat(current, future);
    }

    /**
     * Сохранить новый курс — вызывается симулятором.
     * Сохраняем в БД, ПОСЛЕ успешной записи публикуем подписчикам.
     */
    public Mono<Rate> saveRate(Rate rate) {
        return rateRepository.save(rate)
            .doOnSuccess(saved -> {
                Sinks.EmitResult result = updates.tryEmitNext(saved);
                if (result.isFailure()) {
                    log.warn("Failed to broadcast rate {}: {}", saved.getPair(), result);
                }
                log.debug("Saved rate: {} = {}", saved.getPair(), saved.getRate());
            });
    }
}
