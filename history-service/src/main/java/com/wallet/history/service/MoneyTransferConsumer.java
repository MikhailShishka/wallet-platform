package com.wallet.history.service;

import com.wallet.history.dto.MoneyTransferredEvent;
import com.wallet.history.entity.TransactionHistoryEntry;
import com.wallet.history.repository.TransactionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Kafka consumer для money-transfers.
 *
 * ИДЕМПОТЕНТНОСТЬ:
 * 1. Проверяем existsByEventId — если уже обработали, пропускаем.
 * 2. Даже если между check и save приходит дубль (race при рестарте) —
 *    unique index на eventId в Mongo бросит DuplicateKeyException, ловим.
 *
 * ACK-стратегия:
 * - MANUAL_IMMEDIATE (задан в application.yml).
 * - ack.acknowledge() ТОЛЬКО после успешной обработки.
 * - Если в save() исключение — offset НЕ коммитим → Kafka пришлёт снова.
 *   При рестарте consumer заберёт с того же offset. Дубликат обработается
 *   идемпотентно через existsByEventId.
 *
 * GROUP: 'history-service'. Если добавить второй инстанс с той же группой →
 * Kafka автоматически разделит партиции между ними (rebalance).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoneyTransferConsumer {

    private final TransactionHistoryRepository historyRepository;

    @KafkaListener(
        topics = "${history.events.money-transfers-topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMoneyTransferred(MoneyTransferredEvent event, Acknowledgment ack) {
        try {
            // Быстрая проверка идемпотентности
            if (historyRepository.existsByEventId(event.eventId())) {
                log.debug("Skip duplicate: eventId={}", event.eventId());
                ack.acknowledge();
                return;
            }

            TransactionHistoryEntry entry = TransactionHistoryEntry.builder()
                .eventId(event.eventId())
                .walletId(event.walletId())
                .type(event.type())
                .counterpartyWalletId(event.counterpartyWalletId())
                .transferGroupId(event.transferGroupId())
                .amount(event.amount())
                .currency(event.currency())
                .occurredAt(event.occurredAt())
                .receivedAt(Instant.now())
                .build();

            try {
                historyRepository.save(entry);
                log.info("Saved: eventId={}, walletId={}, type={}, amount={}",
                        event.eventId(), event.walletId(), event.type(), event.amount());
            } catch (DuplicateKeyException e) {
                // Race с другим consumer'ом: check прошёл, но пока сохраняли — дубль
                log.debug("Race caught by unique index: eventId={}", event.eventId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            // Не ack'аем → Kafka пришлёт снова.
            // ⚠️ Без dead-letter topic и exponential backoff это может привести к
            // «застреванию» на битом сообщении. В проде обязательно добавить DLT.
            log.error("Failed to process eventId={}: {}", event.eventId(), e.getMessage(), e);
            throw e;
        }
    }
}
