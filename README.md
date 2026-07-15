# 💰 Wallet Platform

**Production-ready микросервисная платформа электронного кошелька — deposit, withdraw, transfer, конвертация валют — с полным DevOps-циклом.**

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)
![Kafka](https://img.shields.io/badge/Kafka-3.7-black)
![Docker](https://img.shields.io/badge/Docker-multi--stage-blue)
![Kubernetes](https://img.shields.io/badge/Kubernetes-ready-326CE5)
![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF)

---

## 📖 О проекте

Wallet Platform — микросервисная система для управления кошельками пользователей с транзакционной корректностью, отказоустойчивостью и полной наблюдаемостью. Проект решает типичные задачи финтеха: **идемпотентность операций**, **защита от race conditions** при concurrent-запросах, **atomic publishing** событий между БД и брокером, **распределённые транзакции** через Saga, **eventual consistency** между сервисами.

Собран как reference-имплементация паттернов из литературы (Chris Richardson, Sam Newman, Martin Fowler) в живом коде с полным DevOps-обвесом.

## 🏗️ Архитектура

```
                         ┌─────────────┐
                         │   Client    │
                         └──────┬──────┘
                                │ HTTPS
                                ▼
                       ┌────────────────┐
                       │  api-gateway   │  ← JWT validation, CORS,
                       │  Spring Cloud  │    routing, rate limit
                       │    Gateway     │
                       └───┬────┬───┬───┘
              ┌────────────┘    │   └──────────────┐
              ▼                 ▼                  ▼
      ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
      │   wallet-    │  │   history-   │  │   exchange-      │
      │   service    │  │   service    │  │   rate-service   │
      │  Spring MVC  │  │  Spring MVC  │  │   WebFlux        │
      └──────┬───────┘  └──────▲───────┘  └────────┬─────────┘
             │                 │                   │
             │  Outbox event   │                   │
             │  publisher      │                   │
             ▼                 │                   ▼
      ┌──────────────┐  ┌──────┴───────┐  ┌──────────────────┐
      │  PostgreSQL  │  │    Kafka     │  │  MongoDB         │
      │  (wallets,   │◀─┤ (events)     │  │  (rates)         │
      │ transactions │  │              │  │                  │
      │   outbox)    │  └──────────────┘  └──────────────────┘
      └──────────────┘         ▲
                               │
                       ┌───────┴──────┐
                       │  Redis       │
                       │  (cache,     │
                       │  idempotency)│
                       └──────────────┘
                       
   Observability: OpenTelemetry → Jaeger, JSON logs с trace_id
```

## ✨ Highlights

**Distributed Transactions & Consistency**
- ✅ **Идемпотентность** через UNIQUE(wallet_id, idempotency_key) + Optimistic locking (`@Version`)
- ✅ **Outbox pattern** — atomic БД + Kafka publishing, решает dual write problem
- ✅ **Saga pattern** для распределённых операций (transfer между кошельками)
- ✅ **Deterministic lock ordering** для защиты от deadlocks в concurrent transfers

**Resilience & Observability**
- ✅ **Circuit Breaker + Retry + Timeout + Fallback** через Resilience4j
- ✅ **Distributed tracing** через OpenTelemetry + Jaeger (trace через все 4 сервиса)
- ✅ **JSON logs** с trace_id/span_id (MDC) — корреляция logs ↔ traces
- ✅ **Prometheus метрики** через Spring Actuator + Micrometer

**Architecture Patterns**
- ✅ **CQRS** — wallet-service (command side, PostgreSQL) + history-service (query side, MongoDB)
- ✅ **Event-driven** через Kafka с at-least-once + idempotent consumers
- ✅ **Rich Domain Model** — инварианты защищены в самих сущностях (`Wallet.deposit()`)
- ✅ **Reactive** exchange-rate-service на WebFlux + Reactive Mongo + SSE streaming

**Production Readiness**
- ✅ **Docker** — multi-stage builds, non-root user, healthchecks для всех сервисов
- ✅ **Kubernetes** — 28 объектов в 11 манифестах (Deployments, StatefulSets, Services, Ingress, HPA)
- ✅ **CI/CD** — GitHub Actions: build+test matrix, Docker push в GHCR, K8s deploy, Trivy security scan
- ✅ **Тесты** — unit + integration через Testcontainers для всех критичных путей

## 🛠️ Tech Stack

| Категория | Технологии |
|---|---|
| **Language & Runtime** | Java 21, JVM с cgroups awareness |
| **Framework** | Spring Boot 3, Spring Cloud Gateway, Spring Data JPA/MongoDB, Spring WebFlux |
| **Data** | PostgreSQL 15 (транзакции + JPA), MongoDB 7 (event store + read model), Redis 7 (cache) |
| **Messaging** | Apache Kafka 3.7 (KRaft mode, без ZooKeeper) |
| **Reactive** | Project Reactor, WebFlux, SSE |
| **Resilience** | Resilience4j (CircuitBreaker, Retry, Bulkhead, RateLimiter) |
| **Observability** | OpenTelemetry, Jaeger, Micrometer, Prometheus, Logback JSON |
| **Security** | Spring Security, JWT (JJWT), BCrypt |
| **Testing** | JUnit 5, Mockito, Testcontainers, AssertJ |
| **DevOps** | Docker, Docker Compose, Kubernetes, GitHub Actions |
| **Build** | Maven, multi-stage Dockerfile |

## 🚀 Quick Start

### Через Docker Compose (рекомендуется)

```bash
# Собрать и запустить всё (4 сервиса + инфраструктура)
docker-compose up -d --build

# Проверить статус
docker-compose ps

# Логи
docker-compose logs -f api-gateway wallet-service
```

За 1-2 минуты платформа поднимается. Доступ:

| Endpoint | URL |
|---|---|
| API Gateway | http://localhost:8000 |
| wallet-service (direct) | http://localhost:8080 |
| history-service (direct) | http://localhost:8081 |
| exchange-rate-service (direct) | http://localhost:8082 |
| Jaeger UI | http://localhost:16686 |
| Kafka UI | http://localhost:8090 |

### Через Kubernetes (minikube)

```bash
minikube start --cpus=4 --memory=8192
eval $(minikube docker-env)

# Собрать образы (попадут в docker daemon minikube)
docker build -t wallet-service:1.0 wallet-service/
docker build -t history-service:1.0 history-service/
docker build -t exchange-rate-service:1.0 exchange-rate-service/
docker build -t api-gateway:1.0 api-gateway/

minikube addons enable ingress

# Деплой платформы
cd k8s && ./deploy-all.sh
```

Полная инструкция — [`k8s/README.md`](k8s/README.md).

## 📡 API Examples

### 1. Регистрация и логин

```bash
# Регистрация
curl -X POST http://localhost:8000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"[email protected]","password":"secret123"}'

# Логин → получаем JWT
TOKEN=$(curl -s -X POST http://localhost:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"[email protected]","password":"secret123"}' \
  | jq -r .token)
```

### 2. Создание кошелька

```bash
curl -X POST http://localhost:8000/api/v1/wallets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currency":"USD"}'
```

### 3. Deposit (с идемпотентностью)

```bash
# Idempotency-Key защищает от дублей при retry
curl -X POST http://localhost:8000/api/v1/wallets/$WALLET_ID/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"amount":100.00}'
```

### 4. Balance в другой валюте (через exchange-rate)

```bash
# Вызов проходит через Resilience4j (CB + Retry + Fallback)
curl "http://localhost:8000/api/v1/wallets/$WALLET_ID/balance-in/EUR" \
  -H "Authorization: Bearer $TOKEN"
```

### 5. История операций (из history-service)

```bash
curl "http://localhost:8000/api/v1/history/wallets/$WALLET_ID" \
  -H "Authorization: Bearer $TOKEN"
```

## 🔑 Ключевые технические решения

### Идемпотентность денежных операций

Клиент может retry-ить запросы (глюки сети, timeout). Дубли недопустимы.

**Решение** — комбинация двух уровней:

```java
@Transactional
public Transaction deposit(UUID walletId, BigDecimal amount, UUID idempotencyKey) {
    // 1. Проверка idempotency
    return txRepo.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey)
        .orElseGet(() -> {
            // 2. Optimistic lock через @Version на Wallet
            Wallet wallet = walletRepo.findById(walletId).orElseThrow();
            wallet.deposit(amount);  // Rich domain — инвариант в сущности
            walletRepo.save(wallet);

            // 3. UNIQUE(wallet_id, idempotency_key) как последний барьер
            return txRepo.save(new Transaction(walletId, DEPOSIT, amount, idempotencyKey));
        });
}
```

**Что защищает:**
- Race conditions между репликами (K8s replicas=3) → `OptimisticLockException`, retry с тем же ключом даст правильный результат.
- Двойной клик клиента → второй запрос вернёт результат первого без побочных эффектов.
- Конкурентный insert с одним ключом → DB constraint violation → catch → return existing.

### Outbox Pattern — atomic БД + Kafka

Проблема: как гарантировать, что событие о транзакции точно попадёт в Kafka, если Kafka упала между save БД и send? Без Outbox — либо теряем event, либо получаем fake event при rollback.

```java
@Transactional
public Transaction deposit(...) {
    // ... бизнес-логика ...
    Transaction tx = txRepo.save(transaction);

    // Событие в outbox — в ТОЙ ЖЕ транзакции БД (atomic)
    outboxRepo.save(new OutboxRecord(
        UUID.randomUUID(),
        "MoneyDeposited",
        tx.getId().toString(),
        objectMapper.writeValueAsString(new MoneyDepositedEvent(tx))
    ));

    return tx;
}

// Отдельный процесс шлёт в Kafka
@Scheduled(fixedDelay = 500)
public void relay() {
    outboxRepo.findUnpublished(50).forEach(record -> {
        kafkaTemplate.send("wallet-events", record.getAggregateId(), record.getPayload())
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    outboxRepo.markPublished(record.getId());
                }
            });
    });
}
```

**Гарантии:** at-least-once. Событие точно попадёт в Kafka, возможно несколько раз при retry. Consumer в history-service идемпотентен через UNIQUE(event_id).

### Resilience при вызове exchange-rate

`wallet-service` вызывает `exchange-rate-service` для конвертации балансов. Внешний вызов может тормозить или падать — не должно ронять wallet.

```java
@CircuitBreaker(name = "exchangeRate", fallbackMethod = "getCachedRate")
@Retry(name = "exchangeRate")
@TimeLimiter(name = "exchangeRate")
public CompletableFuture<BigDecimal> getRate(String from, String to) {
    return webClient.get()
        .uri("/api/v1/rates/{from}/{to}", from, to)
        .retrieve()
        .bodyToMono(RateResponse.class)
        .map(RateResponse::rate)
        .toFuture();
}

// Fallback — берём последний закэшированный курс из Redis
public CompletableFuture<BigDecimal> getCachedRate(String from, String to, Throwable ex) {
    return CompletableFuture.completedFuture(
        redisTemplate.opsForValue().get("rate:" + from + ":" + to)
    );
}
```

**Конфигурация** (`application.yml`):
- Circuit Breaker: 50% failure rate → OPEN, 10s wait, 3 permitted calls в HALF_OPEN.
- Retry: 3 попытки с exponential backoff (500ms → 1s → 2s).
- TimeLimiter: 3 секунды.
- Fallback: кэшированный курс из Redis (TTL 15 минут).

### Distributed Tracing через 4 сервиса

OpenTelemetry javaagent автоматически инструментирует Spring MVC, JDBC, HTTP clients, Kafka producer/consumer. Trace идёт:

```
api-gateway → wallet-service → PostgreSQL → OutboxRelay → Kafka →
              history-service → MongoDB
```

В Jaeger UI видно waterfall всех спанов. Каждый лог содержит `trace_id` через MDC:

```json
{
  "@timestamp": "2026-07-13T10:23:45.123Z",
  "level": "INFO",
  "logger": "com.wallet.WalletService",
  "message": "Deposit processed for wallet=abc123",
  "trace_id": "5b8aa5a2d2c872e8321cf3a41aa39ce7",
  "span_id": "051581bf3cb55c13"
}
```

Корреляция: увидел медленную операцию в Jaeger → взял trace_id → нашёл все логи по этой операции через 4 сервиса.

## 🐳 Deployment

### Docker

Все 4 сервиса имеют production-ready Dockerfile:
- **Multi-stage build**: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`
- **Non-root user** для безопасности
- **Healthcheck** через `/actuator/health/liveness`
- **JVM с `-XX:MaxRAMPercentage=75`** для правильной работы с cgroups
- Финальный размер образа ~200 MB (вместо 1.5 GB single-stage)

### Kubernetes (11 манифестов, 28 объектов)

```
k8s/
├── 00-namespace.yaml
├── 10-postgres.yaml           # StatefulSet + Secret + Service + PVC
├── 11-mongo.yaml              # StatefulSet + Service + PVC
├── 12-redis.yaml              # Deployment + Service
├── 13-kafka.yaml              # StatefulSet (KRaft mode) + Service
├── 14-jaeger.yaml             # Deployment + Service
├── 20-wallet-service.yaml     # + ConfigMap + Secret + Deployment + Service
├── 21-history-service.yaml    # + ConfigMap + Deployment + Service
├── 22-exchange-rate-service.yaml
├── 23-api-gateway.yaml
├── 30-ingress-hpa.yaml        # Ingress + HPA (2-10 pods по CPU/memory)
├── deploy-all.sh
└── teardown.sh
```

**Ключевые особенности:**
- StatefulSet для БД с PVC — данные переживают рестарт.
- Startup probe (150s) для медленного старта Spring Boot.
- Rolling Update с `maxUnavailable: 0` — zero-downtime.
- HPA для wallet-service — автоскейл под нагрузкой.
- Defense in depth: Ingress только на api-gateway, backend в private network.

### CI/CD — GitHub Actions

`.github/workflows/`:
- **`ci.yml`** — на PR и push в main. Matrix: 4 сервиса параллельно [build + test + integration tests + OWASP scan].
- **`build-and-push.yml`** — на push в main. Matrix: 4 Docker образа с BuildKit кэшированием, Trivy сканирование, push в GHCR.
- **`deploy.yml`** — деплой в K8s с выбором окружения (dev/staging/prod).

Плюс **Dependabot** — weekly PR с обновлениями Maven-зависимостей, Docker base images, GitHub Actions.

## 📊 Observability

### Prometheus метрики

Все 4 сервиса отдают через `/actuator/prometheus`:

**RED metrics** (user-facing):
- `http_server_requests_seconds_count` — rate
- `http_server_requests_seconds_count{status=~"5.."}` — errors
- `histogram_quantile(0.95, ...)` — p95 latency

**JVM metrics**: heap, GC time, threads, non-heap.

**Resilience4j metrics**:
- `resilience4j_circuitbreaker_state` — CB состояние
- `resilience4j_calls_total{kind="failed"}` — счётчик неудачных вызовов

**Business metrics**:
- Транзакции/минуту (deposit + withdraw + transfer)
- Средняя сумма перевода
- Idempotency-hit rate (сколько retry поймано)

### Distributed Tracing

Jaeger UI на http://localhost:16686. Trace идёт через все 4 сервиса + Kafka. Waterfall показывает, где именно медленно.

## 🧪 Тестирование

- **Unit tests** — изолированная бизнес-логика с Mockito.
- **Integration tests** через Testcontainers — реальные PostgreSQL, MongoDB, Redis, Kafka в контейнерах на время теста.
- **Concurrency tests** — 20 параллельных deposit-ов проверяют, что баланс сходится ровно.
- **Idempotency tests** — 100 повторов одного запроса с идентичным ключом дают ровно одну транзакцию.

## 📁 Структура проекта

```
wallet-platform/
├── api-gateway/                    # Spring Cloud Gateway (WebFlux)
├── wallet-service/                 # Command side (MVC + PostgreSQL)
├── history-service/                # Read side (MVC + MongoDB, CQRS)
├── exchange-rate-service/          # Reactive (WebFlux + Reactive Mongo)
├── docker-compose.yml              # 11 сервисов: 4 наших + инфра
├── k8s/                            # 11 манифестов, 28 K8s объектов
├── .github/
│   ├── workflows/                  # CI/CD: ci, build-and-push, deploy
│   └── dependabot.yml
├── run-with-otel.sh                # запуск с OpenTelemetry javaagent
└── README.md
```

## 🎯 Что демонстрирует проект

**Backend engineering:** distributed transactions, idempotency, concurrency, event-driven архитектура, CQRS, реактивщина, resilience patterns, observability.

**Architecture:** микросервисная декомпозиция по bounded contexts, hexagonal-подход, DDD terminology (aggregates, value objects, domain events), rich domain model.

**Ops:** контейнеризация с best practices (multi-stage, non-root, healthchecks), Kubernetes-ready с манифестами, полный CI/CD цикл, Prometheus/Jaeger интеграция.

Готово к деплою в любой Kubernetes-кластер (minikube, EKS, GKE, DigitalOcean).

---

**Проект как reference-имплементация паттернов из литературы (Chris Richardson "Microservices Patterns", Sam Newman "Building Microservices", Martin Fowler) в живом рабочем коде.**
