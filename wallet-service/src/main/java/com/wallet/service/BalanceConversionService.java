package com.wallet.service;

import com.wallet.client.RateClient;
import com.wallet.dto.BalanceInCurrencyDto;
import com.wallet.dto.RateResponse;
import com.wallet.entity.Wallet;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Сервис конверсии баланса из валюты кошелька в целевую.
 *
 * Использует RateClient (с полной защитой Resilience4j).
 * RateClient возвращает CompletableFuture, здесь мы «раскрываем» его
 * через .get() с timeout — потому что находимся в MVC (блокирующем стеке).
 *
 * Метка `stale` = true, если курс из fallback-кэша (последнее известное значение).
 * Правило свежести: если курсу больше 5 минут — считаем stale, даже если downstream ответил
 * (защита от «залипшего» тика в exchange-rate).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceConversionService {

    private final WalletRepository walletRepository;
    private final RateClient rateClient;

    /** Курс считается свежим не дольше этого срока. */
    private static final Duration FRESHNESS_THRESHOLD = Duration.ofMinutes(5);

    @Transactional(readOnly = true)
    public BalanceInCurrencyDto getBalanceInCurrency(UUID walletId, String targetCurrency) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException(walletId));

        // Если целевая валюта совпадает — конвертация не нужна
        if (wallet.getCurrency().equalsIgnoreCase(targetCurrency)) {
            return new BalanceInCurrencyDto(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getBalance(),
                targetCurrency,
                wallet.getBalance(),
                BigDecimal.ONE,
                Instant.now(),
                false
            );
        }

        String pair = wallet.getCurrency().toUpperCase() + "-" + targetCurrency.toUpperCase();
        RateResponse rate = fetchRate(pair);

        boolean stale = isStale(rate);
        BigDecimal converted = wallet.getBalance()
            .multiply(rate.rate())
            .setScale(2, RoundingMode.HALF_UP);

        return new BalanceInCurrencyDto(
            wallet.getId(),
            wallet.getCurrency(),
            wallet.getBalance(),
            targetCurrency,
            converted,
            rate.rate(),
            rate.occurredAt(),
            stale
        );
    }

    /**
     * Блокирующее получение курса из асинхронного клиента.
     * timeout .get() чуть больше, чем TimeLimiter — доп. защита на случай зависания future.
     */
    private RateResponse fetchRate(String pair) {
        try {
            return rateClient.getRate(pair).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching rate " + pair, e);
        } catch (ExecutionException e) {
            // Внутри — либо RateUnavailableException (полный отказ), либо непредвиденное
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Failed to fetch rate " + pair, cause);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout fetching rate " + pair, e);
        }
    }

    private boolean isStale(RateResponse rate) {
        if (rate.occurredAt() == null) return true;
        Duration age = Duration.between(rate.occurredAt(), Instant.now());
        return age.compareTo(FRESHNESS_THRESHOLD) > 0;
    }
}
