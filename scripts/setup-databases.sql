-- BingoPlus Multi-Tenant Database Setup
-- Run this script as a PostgreSQL superuser to create
-- the master database and initial tenant structure.
-- ===================================================

CREATE DATABASE bingo_master
    WITH ENCODING 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TEMPLATE template0;

\c bingo_master

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    admin_user_id BIGINT,
    parent_id BIGINT REFERENCES users(id),
    business_name VARCHAR(255),
    admin_approved BOOLEAN NOT NULL DEFAULT FALSE,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tenant_registry (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT UNIQUE NOT NULL,
    database_name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS invite_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    creator_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS admin_fund_requests (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    screenshot_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT,
    approved_at TIMESTAMP,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_telegram ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_users_admin ON users(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_users_parent ON users(parent_id);
CREATE INDEX IF NOT EXISTS idx_tenant_registry_admin ON tenant_registry(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_invite_codes_code ON invite_codes(code);
CREATE INDEX IF NOT EXISTS idx_invite_codes_creator ON invite_codes(creator_id);
CREATE INDEX IF NOT EXISTS idx_admin_fund_admin ON admin_fund_requests(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_admin_fund_status ON admin_fund_requests(status);

-- Tenant databases are auto-created by the application when new admins register.
-- Each database is named bingo_agent_{adminUserId}.
