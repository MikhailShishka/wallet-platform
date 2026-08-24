# ============================================================
# wallet-service Dockerfile
# Multi-stage build:
#   Stage 1: builder — Maven + JDK, компилируем и упаковываем.
#   Stage 2: runtime — только Alpine JRE + наш jar.
#
# Финальный образ: ~200 MB (вместо ~1.5 GB для single-stage).
# ============================================================

# --------- Stage 1: BUILDER ---------
FROM maven:3-eclipse-temurin-24 AS builder

WORKDIR /build

# 1. Копируем только pom.xml (редко меняется)
COPY pom.xml .

# 2. Скачиваем все зависимости offline. Этот слой кэшируется
#    и не пересобирается при изменении кода.
RUN mvn dependency:go-offline -B

# 3. Копируем исходники (часто меняются, поэтому ниже)
COPY src ./src

# 4. Собираем jar без тестов (тесты гоняем в CI отдельно)
RUN mvn clean package -DskipTests -B

# --------- Stage 2: RUNTIME ---------
FROM eclipse-temurin:21-jre-alpine

# 5. Устанавливаем wget для healthcheck (Alpine минималистичный)
RUN apk add --no-cache wget

# 6. Создаём непривилегированного пользователя
#    -S = system user (без пароля, без login shell)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 7. Копируем ТОЛЬКО собранный jar из builder-стадии
COPY --from=builder /build/target/*.jar app.jar

# 8. Меняем владельца на непривилегированного
RUN chown -R appuser:appgroup /app

# 9. Переключаемся на appuser (никакого root в runtime)
USER appuser

# 10. Документируем порт (декларативно, не открывает автоматически)
EXPOSE 8080

# 11. Healthcheck через Spring Boot Actuator liveness probe.
#     start-period=60s — Spring Boot стартует небыстро, даём разогреться.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# 12. Запуск с настройками JVM для контейнера:
#     - MaxRAMPercentage=75 — использует 75% лимита памяти контейнера
#     - -XX:+UseContainerSupport (в JDK 10+ включено по умолчанию, но явно надёжнее)
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+UseContainerSupport", \
    "-jar", "app.jar"]
