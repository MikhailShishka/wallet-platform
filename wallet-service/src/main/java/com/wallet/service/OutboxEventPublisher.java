package com.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.MoneyTransferredEvent;
import com.wallet.entity.OutboxRecord;
import com.wallet.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Кладёт события в outbox — вызывается ИЗ бизнес-транзакции сервиса.
 *
 * Ключевое: НЕТ @Transactional. Компонент участвует в вызывающей транзакции
 * (WalletOperationService.transfer уже помечен @Transactional).
 * Поэтому INSERT в outbox коммитится атомарно с изменением баланса и записью в ledger.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${wallet.events.money-transfers-topic}")
    private String moneyTransfersTopic;

    /**
     * Сохранить событие MoneyTransferred в outbox.
     * Kafka пока не трогаем — этим займётся OutboxRelay в другом потоке.
     */
    public void publish(MoneyTransferredEvent event) {
        try {
            OutboxRecord record = new OutboxRecord();
            record.setAggregateId(event.walletId());
            record.setTopic(moneyTransfersTopic);
            record.setEventType(event.type());
            record.setPayload(objectMapper.writeValueAsString(event));
            outboxRepository.save(record);

            log.debug("Outbox appended: type={}, walletId={}, eventId={}",
                    event.type(), event.walletId(), event.eventId());
        } catch (JsonProcessingException e) {
            // Ретрай не поможет — падаем, вся бизнес-транзакция откатится.
            // Так и должно быть: если не можем сериализовать событие,
            // не должны позволять бизнес-операции пройти.
            throw new IllegalStateException("Failed to serialize event: " + event, e);
        }
    }
}
