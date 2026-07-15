package com.wallet.rates.repository;

import com.wallet.rates.entity.Rate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Реактивный репозиторий Mongo.
 * Отличие от обычного MongoRepository — возвращаемые типы Mono/Flux.
 * Ни один вызов НЕ БЛОКИРУЕТ вызывающую нить.
 */
@Repository
public interface RateRepository extends ReactiveMongoRepository<Rate, String> {

    /** Последний зафиксированный курс по паре — 1 значение (или пусто). */
    Mono<Rate> findFirstByPairOrderByOccurredAtDesc(String pair);

    /** Стрим истории курсов по паре, свежие сверху. */
    Flux<Rate> findByPairOrderByOccurredAtDesc(String pair);
}
