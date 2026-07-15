# 💰 wallet-service

Ядро электронного кошелька. Spring Boot 3 + PostgreSQL + Hibernate + Flyway + Spring Security + JWT + Actuator/Micrometer + OpenAPI/Swagger + Redis + Kafka (outbox) + Resilience4j.

## ✅ Текущий статус — Блок 8 (Микросервисы)

- Всё из Блоков 3-6 + Resilience4j для защиты вызова exchange-rate-service.
- 🆕 **Resilience4j:**
  - `@CircuitBreaker(name="exchange-rate")` — sliding window 20 вызовов, порог 50%, wait 10s.
  - `@Retry` — 3 попытки, exponential backoff 100→200→400ms, только на «сетевых» ошибках.
  - `@TimeLimiter` — 3 сек на каждый вызов.
  - Fallback → локальный stale cache (`ConcurrentHashMap`).
  - Полный отказ → 503 через `RateUnavailableException`.
- 🆕 **Новый эндпоинт:** `GET /api/v1/wallets/{id}/balance-in/{targetCurrency}`.
  - Использует `RateClient` с защитой.
  - Возвращает `{ walletBalance, convertedBalance, rate, rateOccurredAt, stale }`.
  - Поле `stale` = true, если курс из кэша или устарел (>5 мин).

- Всё из Блоков 3-5: каркас, JPA, Security, тесты, метрики, Swagger, Redis-кэш.
- 🆕 **Kafka Producer через Outbox pattern:**
  - Таблица `outbox` (Flyway V2) — JSONB payload, индексы на pending и sent.
  - `OutboxEventPublisher` — вспомогательный компонент, вызывается **внутри** бизнес-транзакции.
  - `OutboxRelay` — `@Scheduled(fixedDelay=2s)`, читает pending, отправляет в Kafka.
  - `KafkaTopicConfig` — автосоздание топика `money-transfers` (3 партиции).
  - Producer config: `acks=all`, `enable.idempotence=true`, `retries=3`.
  - Каждый deposit/withdraw/transfer публикует `MoneyTransferredEvent`.
  - **Ключ Kafka = walletId** → одна партиция для всех событий кошелька → порядок гарантирован.

### Как это работает
```
transfer() → @Transactional {
    UPDATE wallets (balance)      ┐
    INSERT INTO transactions      │ ← одна транзакция БД
    INSERT INTO outbox            ┘   Postgres либо всё зафиксирует, либо всё откатит
}
                    ↓
OutboxRelay (@Scheduled, каждые 2 сек):
    SELECT * FROM outbox WHERE sent_at IS NULL
    → KafkaTemplate.send(topic, walletId, payload)
    → UPDATE outbox SET sent_at = now()
```

### Что кэшируется (и что НЕ кэшируется)
| Данные | Кэш? | Почему |
|---|---|---|
| Профиль пользователя (`User`) | ✅ 15 мин TTL | редко меняется, часто читается |
| Курсы валют (future) | ✅ 60 сек TTL | горячие, меняются медленно |
| Баланс кошелька (`Wallet`) | ❌ | финансово критично + `@Version` конфликтует |
| История транзакций | ❌ | растёт постоянно, сложная инвалидация |
| JWT-токены | ❌ | уже stateless |

### Полный список эндпоинтов
| Метод | Путь | Что | Auth |
|---|---|---|---|
| POST | `/api/v1/users/register` | регистрация | открыто |
| POST | `/api/v1/auth/login` | логин, вернёт JWT | открыто |
| POST | `/api/v1/wallets` | создать свой кошелёк | JWT + userId = свой |
| GET | `/api/v1/wallets/{id}` | получить кошелёк | JWT + владение |
| GET | `/api/v1/wallets/user/{userId}` | список своих кошельков | JWT + свой userId |
| POST | `/api/v1/wallets/{id}/deposit` | пополнить | JWT + владение + Idempotency-Key |
| POST | `/api/v1/wallets/{id}/withdraw` | снять | JWT + владение + Idempotency-Key |
| POST | `/api/v1/wallets/{id}/transfer` | перевести | JWT + владение + Idempotency-Key |
| GET | `/api/v1/wallets/{id}/transactions` | история | JWT + владение |
| GET | `/actuator/health` | health-check | открыто |
| GET | `/actuator/health/liveness` | K8s liveness | открыто |
| GET | `/actuator/health/readiness` | K8s readiness | открыто |
| GET | `/actuator/prometheus` | Prometheus scrape | открыто (в prod — на порту 9090) |

**⚠️ Что НЕ реализовано (по плану обучения):**
- Kafka, history-service — Блок 6.
- exchange-rate-service — Блок 7.
- Refresh-токены — будущая итерация.
- Docker multi-stage build — Блок 10.
- Kubernetes манифесты — Блок 11.

---

## 🚀 Локальный запуск

### 1. Поднять Postgres
```bash
docker-compose up -d
```

### 2. Собрать и запустить
```bash
./mvnw spring-boot:run
# или с dev-профилем (видны SQL):
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Приложение поднимется на `http://localhost:8080`.

### 3. Проверка
```bash
curl http://localhost:8080/actuator/health
```

---

## 🧪 Запуск тестов

Требование: **Docker должен быть запущен** (для Testcontainers).

```bash
# Все тесты
./mvnw test

# Только unit (быстро, ~2 сек, без Docker)
./mvnw test -Dtest='*Test' -Dtest.excluded='*IntegrationTest,*RepositoryTest'

# Только интеграционные с Testcontainers
./mvnw test -Dtest='*IntegrationTest,*RepositoryTest'
```

### Что где тестируется

| Слой | Тест | Что проверяет | Скорость |
|---|---|---|---|
| Unit | `UserServiceTest`, `WalletServiceTest`, `JwtServiceTest` | бизнес-логику класса в изоляции (Mockito) | миллисекунды |
| Slice | `WalletControllerTest` (`@WebMvcTest`) | контроллер + MVC + валидация + JSON | сотни мс |
| Integration | `UserRepositoryTest` (`@DataJpaTest`) | реальные SQL-запросы к Postgres | секунды |
| E2E | `AuthFlowIntegrationTest` (`@SpringBootTest`) | полный флоу с Security, БД, HTTP | секунды |

**Все интеграционные тесты используют общий Postgres-контейнер** (`AbstractIntegrationTest`), контейнер стартует один раз на JVM.

---

## 📡 Примеры запросов

### 1. Регистрация (открыто)
```bash
curl -X POST http://localhost:8080/api/v1/users/register \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "alice@example.com",
    "password": "secret123",
    "firstName": "Alice",
    "lastName": "Wonderland"
  }'
```

### 2. Логин — получаем JWT
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "alice@example.com",
    "password": "secret123"
  }'
```
Ответ:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer"
}
```

### 3. Создание кошелька (требует токен)
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
USER_ID="550e8400-e29b-41d4-a716-446655440000"

curl -X POST http://localhost:8080/api/v1/wallets \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"userId\":\"$USER_ID\",\"currency\":\"USD\"}"
```

### 4. Без токена — 401
```bash
curl http://localhost:8080/api/v1/wallets/user/$USER_ID
# {"code":"UNAUTHORIZED","message":"...","timestamp":"..."}
```

### 5. Пополнение (deposit) — требует Idempotency-Key
```bash
WALLET_ID="..."   # из ответа createWallet

curl -X POST "http://localhost:8080/api/v1/wallets/$WALLET_ID/deposit" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount": 100.50}'
```
Повтор с тем же `Idempotency-Key` вернёт **ту же** транзакцию, ничего не изменит.

### 6. Снятие (withdraw)
```bash
curl -X POST "http://localhost:8080/api/v1/wallets/$WALLET_ID/withdraw" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount": 30.00}'
```
Не хватает средств → 422 `INSUFFICIENT_FUNDS`.

### 7. Перевод (transfer)
```bash
curl -X POST "http://localhost:8080/api/v1/wallets/$FROM_ID/transfer" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"toWalletId\": \"$TO_ID\", \"amount\": 20.00}"
```

### 8. История операций (пагинация)
```bash
curl "http://localhost:8080/api/v1/wallets/$WALLET_ID/transactions?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### 9. Попытка чужого кошелька — 403
```bash
# Логинимся под другим юзером и пытаемся смотреть кошелёк Алисы
curl "http://localhost:8080/api/v1/wallets/$ALICE_WALLET_ID" \
  -H "Authorization: Bearer $OTHER_TOKEN"
# → 403 FORBIDDEN
```

---

## 🔐 Безопасность — что под капотом

- **BCrypt** для паролей (strength 10) — никакого plain text.
- **JWT** в `Authorization: Bearer ...` — stateless аутентификация.
- **HS256** подпись через `jwt.secret` (мин. 32 байта).
- **`SessionCreationPolicy.STATELESS`** — Spring не создаёт HttpSession.
- **CSRF disabled** — JWT в заголовке не уязвим к CSRF.
- **Одинаковое сообщение** для «нет пользователя» и «неверный пароль» — защита от user enumeration.
- **`@PreAuthorize`** проверка владения на каждом эндпоинте.
- **`@Version`** на Wallet — защита от двойного списания.
- **Идемпотентность** через `Idempotency-Key` + UNIQUE в БД.

⚠️ В проде:
- `JWT_SECRET`, `DB_PASSWORD` — через переменные окружения, не в файле.
- TLS обязателен — иначе токен можно перехватить.

---

## 📊 Observability

### Endpoints Actuator
- `GET /actuator/health` — состояние (open).
- `GET /actuator/health/liveness` — Kubernetes liveness probe.
- `GET /actuator/health/readiness` — Kubernetes readiness probe (падает, если БД недоступна).
- `GET /actuator/info` — метаданные (`app.name`, `app.version`).
- `GET /actuator/metrics` — список всех метрик (dev only в prod).
- `GET /actuator/prometheus` — метрики в формате Prometheus (для scrape).

### Автоматические метрики (из коробки)
- **JVM:** heap, GC pauses, threads, uptime.
- **HTTP:** `http_server_requests_seconds` — RPS, latency, статус-коды.
- **HikariCP:** активные/idle connections.
- **Tomcat:** threads, sessions.

### Бизнес-метрики
- `wallet_operations_total{type="deposit"}` — количество пополнений.
- `wallet_operations_total{type="withdraw"}` — количество снятий.
- `wallet_operations_total{type="transfer"}` — количество переводов.
- `wallet_errors_total{type="insufficient_funds"}` — отказы по балансу.
- `wallet_transfer_duration_seconds` — время исполнения transfer.

### Профили конфигурации
- `dev` — SQL в логах, все actuator-эндпоинты открыты, DEBUG.
- `prod` — актуатор на порту 9090, секреты только через env, JWT 15 мин.

Активация: `--spring.profiles.active=prod` или `SPRING_PROFILES_ACTIVE=prod`.

### Пример запуска в prod
```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://db.internal:5432/wallet
export SPRING_DATASOURCE_USERNAME=wallet
export SPRING_DATASOURCE_PASSWORD=<from-secret-store>
export JWT_SECRET=<64-byte-random>
./mvnw spring-boot:run
```

---

## 🏗️ Структура проекта

```
src/main/java/com/wallet/
├── WalletApplication.java
├── controller/        — REST-эндпоинты
├── service/           — бизнес-логика, @Transactional
├── repository/        — Spring Data JPA
├── entity/            — @Entity (User, Wallet)
├── dto/               — request/response объекты
├── exception/         — кастомные исключения + GlobalExceptionHandler
└── config/            — @Configuration

src/main/resources/
├── application.yml
├── application-dev.yml
└── db/migration/
    └── V1__init_schema.sql
```

## 💡 Архитектурные решения

См. корневой `README.md` курса. Кратко:
- **UUID** идентификаторы.
- **DECIMAL(19,4)** для денег (`BigDecimal` в Java, никогда float).
- **`@Version`** на `Wallet` — оптимистическая блокировка.
- **LAZY всегда** на @ManyToOne.
- **DTO ≠ Entity** — отделяем API-контракт от БД-модели.
- **`@RestControllerAdvice`** — централизованная обработка ошибок.
