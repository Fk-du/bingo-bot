-- BingoPlus Multi-Tenant Database Setup
-- Run this script as a PostgreSQL superuser to create
-- the master database and initial tenant structure.
-- ===================================================

-- 1. Create master database (user auth + tenant registry)
-- This MUST exist before the application can start.
CREATE DATABASE bingo_master
    WITH ENCODING 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TEMPLATE template0;

-- 2. Connect to bingo_master and create the initial tables
-- (Hibernate will auto-create tables on startup via ddl-auto=update)
\c bingo_master

-- Users table (ALL users: SUPER_ADMIN, ADMIN, PLAYER)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    agent_id BIGINT,
    parent_id BIGINT REFERENCES users(id),
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tenant registry
CREATE TABLE IF NOT EXISTS tenant_registry (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT UNIQUE NOT NULL,
    database_name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Invite codes (cross-tenant, stored in master DB for registration flow)
CREATE TABLE IF NOT EXISTS invite_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    creator_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Agent fund requests (admin requests funds from super admin)
CREATE TABLE IF NOT EXISTS agent_fund_requests (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    screenshot_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT,
    approved_at TIMESTAMP,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_telegram ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_users_agent ON users(agent_id);
CREATE INDEX IF NOT EXISTS idx_users_parent ON users(parent_id);
CREATE INDEX IF NOT EXISTS idx_tenant_registry_agent ON tenant_registry(agent_id);
CREATE INDEX IF NOT EXISTS idx_invite_codes_code ON invite_codes(code);
CREATE INDEX IF NOT EXISTS idx_invite_codes_creator ON invite_codes(creator_id);
CREATE INDEX IF NOT EXISTS idx_agent_fund_agent ON agent_fund_requests(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_fund_status ON agent_fund_requests(status);

-- 3. Tenant databases are auto-created by the application
-- when new admins are registered. Each database is named
-- bingo_agent_{adminId} and contains all game/domain tables.
--
-- To manually create a tenant database:
--   CREATE DATABASE bingo_agent_1;
