package com.wallet.rates.controller;

import com.wallet.rates.dto.RateDto;
import com.wallet.rates.service.RateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * REST API курсов валют.
 *
 * Два стиля:
 * 1. Обычный GET (JSON) → Mono<RateDto>.
 * 2. SSE-стрим (Server-Sent Events) → Flux<RateDto>.
 *    Клиент подключается один раз и получает поток новых курсов.
 *
 * ЗАМЕТЬ: возвращаем НЕ RateDto, а Mono<RateDto>/Flux<RateDto>.
 * Spring WebFlux сам подпишется, дождётся и отправит клиенту.
 */
@RestController
@RequestMapping("/api/v1/rates")
@RequiredArgsConstructor
public class RateController {

    private final RateService rateService;

    /**
     * Текущий курс.
     * GET /api/v1/rates/USD-EUR → { pair, rate, occurredAt }
     * Если пары нет → 404.
     */
    @GetMapping("/{pair}")
    public Mono<RateDto> getRate(@PathVariable String pair) {
        return rateService.getCurrentRate(pair)
            .map(RateDto::from)
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "No rate found for pair " + pair
            )));
    }

    /**
     * SSE стрим свежих курсов.
     * GET /api/v1/rates/stream/USD-EUR (Accept: text/event-stream)
     *
     * Клиент подключается через EventSource в браузере или curl -N.
     * Получает сразу текущий курс, потом каждые 5 сек новые.
     *
     * onBackpressureLatest: если клиент медленный (медленная сеть, зависший
     * браузер), сохраняем ТОЛЬКО последний курс, старые отбрасываем.
     * Для курсов это ок: устаревший всё равно ни для чего не нужен.
     *
     * takeWhile / take: ограничиваем время подключения, чтобы не держать
     * коннект бесконечно (для примера — 10 минут).
     */
    @GetMapping(value = "/stream/{pair}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<RateDto> streamRates(@PathVariable String pair) {
        return rateService.streamRates(pair)
            .onBackpressureLatest()
            .map(RateDto::from)
            .take(Duration.ofMinutes(10));
    }
}
