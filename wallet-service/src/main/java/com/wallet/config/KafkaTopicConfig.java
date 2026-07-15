package com.wallet.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Автосоздание Kafka топиков при старте приложения.
 *
 * Загружается только если KafkaAdmin bean присутствует
 * (в тестах KafkaAutoConfiguration отключена → bean'а нет → конфиг не грузится).
 */
@Configuration
@ConditionalOnClass(KafkaAdmin.class)
public class KafkaTopicConfig {

    @Value("${wallet.events.money-transfers-topic}")
    private String moneyTransfersTopic;

    @Bean
    public NewTopic moneyTransfersTopic() {
        return TopicBuilder.name(moneyTransfersTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
