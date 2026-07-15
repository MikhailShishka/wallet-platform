-- ============================================================
-- Electronic Wallet — DB Schema for wallet-service
-- PostgreSQL 14+
-- ============================================================

-- Расширение для UUID-функции
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'Пользователи системы';
COMMENT ON COLUMN users.password_hash IS 'bcrypt/argon2 хэш, не plain text!';


-- ============================================================
-- WALLETS — счета пользователей в разных валютах
-- ============================================================
CREATE TABLE wallets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    currency        VARCHAR(3) NOT NULL CHECK (currency IN ('RUB', 'USD', 'EUR')),
    balance         DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (user_id, currency)
);

CREATE INDEX idx_wallets_user_id ON wallets(user_id);

COMMENT ON TABLE wallets IS 'Счета: один пользователь × одна валюта = один счёт';
COMMENT ON COLUMN wallets.balance IS 'Текущий баланс. Источник правды — таблица transactions';
COMMENT ON COLUMN wallets.version IS 'Для @Version (оптимистическая блокировка)';


-- ============================================================
-- TRANSACTIONS — append-only ledger всех движений
-- ============================================================
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id           UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    type                VARCHAR(20) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER')),
    amount              DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    transfer_group_id   UUID,
    idempotency_key     UUID NOT NULL UNIQUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
    -- БЕЗ updated_at: транзакции неизменяемы (append-only)
);

-- Главный индекс под запрос «история кошелька, новые сверху»
CREATE INDEX idx_transactions_wallet_id_created_at
    ON transactions(wallet_id, created_at DESC);

-- Индекс под поиск пары для TRANSFER
CREATE INDEX idx_transactions_transfer_group_id
    ON transactions(transfer_group_id)
    WHERE transfer_group_id IS NOT NULL;

COMMENT ON TABLE transactions IS 'Append-only ledger. Любая правка ошибки — обратная операция, не UPDATE!';
COMMENT ON COLUMN transactions.amount IS 'Всегда положительная. Знак (списание/зачисление) определяется по type и контексту';
COMMENT ON COLUMN transactions.transfer_group_id IS 'Связывает 2 строки одного перевода (NULL для DEPOSIT/WITHDRAW)';
COMMENT ON COLUMN transactions.idempotency_key IS 'Защита от повторов; UNIQUE гарантирует атомарность';


-- ============================================================
-- Триггер для автоматического обновления updated_at
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_wallets_updated_at BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
