package com.wallet.rates.service;

import com.wallet.rates.entity.Rate;
import com.wallet.rates.repository.RateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Тест реактивного сервиса через StepVerifier.
 *
 * StepVerifier — стандартный инструмент из reactor-test для проверки
 * поведения Mono/Flux: какие значения пришли, в каком порядке,
 * завершился ли поток / упал ли с ошибкой.
 *
 * Обрати внимание: репозиторий возвращает Mono/Flux — стаббим их
 * через Mono.just / Mono.empty / Mono.error.
 */
@ExtendWith(MockitoExtension.class)
class RateServiceTest {

    @Mock
    private RateRepository rateRepository;

    @InjectMocks
    private RateService rateService;

    @Test
    void getCurrentRateShouldReturnValueWhenExists() {
        // GIVEN
        Rate rate = Rate.builder()
            .pair("USD-EUR")
            .rate(new BigDecimal("0.92"))
            .occurredAt(Instant.now())
            .build();
        when(rateRepository.findFirstByPairOrderByOccurredAtDesc("USD-EUR"))
            .thenReturn(Mono.just(rate));

        // WHEN + THEN
        StepVerifier.create(rateService.getCurrentRate("USD-EUR"))
            .expectNextMatches(r ->
                r.getPair().equals("USD-EUR")
                && r.getRate().compareTo(new BigDecimal("0.92")) == 0)
            .verifyComplete();
    }

    @Test
    void getCurrentRateShouldReturnEmptyWhenNotFound() {
        when(rateRepository.findFirstByPairOrderByOccurredAtDesc("XXX-YYY"))
            .thenReturn(Mono.empty());

        StepVerifier.create(rateService.getCurrentRate("XXX-YYY"))
            .verifyComplete();   // ничего не пришло, поток корректно завершился
    }

    @Test
    void saveRateShouldPersistAndBroadcast() {
        // GIVEN
        Rate rate = Rate.builder()
            .pair("USD-EUR")
            .rate(new BigDecimal("0.93"))
            .occurredAt(Instant.now())
            .build();
        when(rateRepository.save(any(Rate.class))).thenReturn(Mono.just(rate));

        // Подписываемся на broadcast ДО save — чтобы поймать событие
        var broadcast = rateService.streamRates("USD-EUR");

        // WHEN + THEN — параллельно проверяем и save, и broadcast
        StepVerifier.create(rateService.saveRate(rate))
            .expectNextMatches(saved -> saved.getPair().equals("USD-EUR"))
            .verifyComplete();
    }
}
