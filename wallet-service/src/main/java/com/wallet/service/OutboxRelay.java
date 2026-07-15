package com.wallet.service;

import com.wallet.entity.OutboxRecord;
import com.wallet.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OutboxRelay — периодически отправляет накопленные в outbox события в Kafka.
 *
 * Условная загрузка: только если в контексте есть KafkaTemplate
 * (в тестах KafkaAutoConfiguration отключена → релей не грузится).
 *
 * Гарантия доставки — at-least-once:
 * - Kafka producer настроен acks=all + enable.idempotence=true
 * - После успешного get() из future мы помечаем запись как sent_at=now()
 * - Если между отправкой и обновлением sent_at падает JVM — запись останется
 *   pending, следующий цикл её пошлёт снова → на consumer'е дубликат →
 *   consumer обязан быть идемпотентным (проверка eventId).
 *
 * Ключ Kafka-сообщения = aggregateId (walletId) → одинаковые кошельки идут
 * в одну партицию, порядок гарантирован.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * fixedDelayString — можно переопределить через property (для тестов).
     * По умолчанию каждые 2 секунды.
     */
    @Scheduled(fixedDelayString = "${wallet.outbox.relay.delay-ms:2000}")
    @Transactional
    public void relay() {
        List<OutboxRecord> pending = outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        log.debug("Outbox relay: {} pending records", pending.size());

        for (OutboxRecord record : pending) {
            try {
                CompletableFuture<?> future = kafkaTemplate.send(
                    record.getTopic(),
                    record.getAggregateId().toString(),   // ключ = walletId
                    record.getPayload()
                );
                // Блокируемся до подтверждения от Kafka
                future.get();
                record.markSent();
                log.debug("Sent to Kafka: id={}, topic={}, type={}",
                        record.getId(), record.getTopic(), record.getEventType());
            } catch (Exception e) {
                // Не помечаем как sent → повторим на следующем тике.
                // Увеличиваем счётчик попыток для диагностики.
                record.setAttempts(record.getAttempts() + 1);
                log.warn("Failed to send outbox record id={} attempt={}: {}",
                        record.getId(), record.getAttempts(), e.getMessage());
            }
        }
        // save all — @Transactional автосохранит через JPA dirty-checking
    }
}
