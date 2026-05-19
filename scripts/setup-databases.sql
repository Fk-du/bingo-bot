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
    role VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    parent_id BIGINT REFERENCES users(id),
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tenant registry
CREATE TABLE IF NOT EXISTS tenant_registry (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT UNIQUE NOT NULL,
    database_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Invite codes (cross-tenant, stored in master DB for registration flow)
CREATE TABLE IF NOT EXISTS invite_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    admin_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_tenant_registry_admin_id ON tenant_registry(admin_id);
CREATE INDEX IF NOT EXISTS idx_invite_codes_code ON invite_codes(code);

-- 3. Tenant databases are auto-created by the application
-- when new admins are registered. Each database is named
-- bingo_agent_{adminId} and contains all game/domain tables.
--
-- To manually create a tenant database:
--   CREATE DATABASE bingo_agent_1;
