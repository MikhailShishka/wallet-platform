package com.wallet.rates.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Snapshot курса валют.
 * Каждый тик симулятора создаёт новую запись — коллекция append-only.
 * Актуальный курс = последняя запись по паре.
 *
 * Индексы:
 * - pair (USD-EUR) для быстрого поиска.
 * - occurredAt для сортировки по времени.
 */
@Document(collection = "rates")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Rate {

    @Id
    private String id;

    /** Пара валют в формате BASE-QUOTE (например, USD-EUR). */
    @Indexed
    private String pair;

    /** Сколько QUOTE за 1 BASE. */
    private BigDecimal rate;

    @Indexed
    private Instant occurredAt;
}
