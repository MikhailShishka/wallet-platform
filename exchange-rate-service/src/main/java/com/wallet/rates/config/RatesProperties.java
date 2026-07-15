package com.wallet.rates.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "rates")
public class RatesProperties {

    /** Пары, которые обслуживаем. */
    private List<String> pairs;

    /** Стартовые курсы (при первом старте засеваются в БД). */
    private Map<String, BigDecimal> seed;

    private SimulatorProperties simulator = new SimulatorProperties();

    @Data
    public static class SimulatorProperties {
        private long intervalMs = 5000;
        private double volatility = 0.005;
    }
}
