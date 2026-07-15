-- ============================================================
-- V2: Outbox pattern для надёжной доставки событий в Kafka
-- ============================================================
-- Событие пишется в эту таблицу в ОДНОЙ транзакции с бизнес-изменением
-- (баланс кошелька + запись в ledger + запись в outbox).
-- Отдельный relay периодически читает pending-записи и отправляет в Kafka.
-- ============================================================

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,            -- id доменной сущности (walletId для переводов)
    topic        VARCHAR(255) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,    -- MONEY_TRANSFERRED, DEPOSIT, WITHDRAW и т.п.
    payload      JSONB NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    sent_at      TIMESTAMP WITH TIME ZONE,
    -- Опционально: количество попыток отправки для наблюдаемости и отладки
    attempts     INTEGER NOT NULL DEFAULT 0
);

-- Индекс для быстрой выборки неотправленных
CREATE INDEX idx_outbox_pending
    ON outbox (created_at)
    WHERE sent_at IS NULL;

-- Индекс для очистки уже отправленных (retention: например, удалять через 7 дней)
CREATE INDEX idx_outbox_sent_at
    ON outbox (sent_at)
    WHERE sent_at IS NOT NULL;

COMMENT ON TABLE outbox IS
    'Outbox pattern: события в очереди на отправку в Kafka. Гарантирует at-least-once доставку в связке с бизнес-транзакцией.';
