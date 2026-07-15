package com.wallet.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Настройка кэширования на Redis.
 *
 * Ключевые решения:
 * 1. JSON serialization (Jackson) вместо Java Serialization —
 *    читаемо в redis-cli, переносимо между сервисами и версиями JVM.
 * 2. JavaTimeModule для Instant / LocalDate.
 * 3. Разные TTL для разных кэшей (short/long-lived данные).
 * 4. Общий префикс "wallet-service::" — если Redis шарится с другими сервисами,
 *    ключи не пересекаются.
 * 5. Отключён cacheNullValues — не кэшируем null-результаты,
 *    иначе после промаха будем возвращать null много раз.
 *
 * @EnableCaching включает обработку @Cacheable / @CacheEvict / @CachePut.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // Базовая конфигурация: JSON serialization, TTL по умолчанию, префикс
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .prefixCacheNameWith("wallet-service::")
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(jsonSerializer()));

        // Тонкая настройка TTL для конкретных кэшей
        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
            "users",  defaultConfig.entryTtl(Duration.ofMinutes(15)),
            // Кэш будущих курсов валют — короткий TTL,
            // курсы меняются каждую минуту у нас, ок было бы 30s.
            "exchange-rates", defaultConfig.entryTtl(Duration.ofSeconds(60))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(perCacheConfig)
            .build();
    }

    /**
     * Jackson с поддержкой:
     * - Java time (Instant, LocalDate) без Long-эпох.
     * - Полиморфных типов (записываем @class в JSON, чтобы восстановить тип объекта).
     */
    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // Полиморфная типизация — нужна, чтобы Redis мог сериализовать любые сущности
        // (в том числе те, у которых поле типа Object или коллекции с разными элементами).
        var validator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(Object.class)
            .build();
        mapper.activateDefaultTyping(validator,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);

        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
