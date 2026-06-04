-- BingoPlus Master Schema
-- Target: bingo_master database
-- Tables: users + tenant_registry + invite_codes

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    parent_id BIGINT REFERENCES users(id),
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tenant_registry (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT UNIQUE NOT NULL,
    database_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS invite_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    admin_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_parent_id ON users(parent_id);
CREATE INDEX IF NOT EXISTS idx_tenant_registry_admin_id ON tenant_registry(admin_id);
CREATE INDEX IF NOT EXISTS idx_invite_codes_code ON invite_codes(code);
