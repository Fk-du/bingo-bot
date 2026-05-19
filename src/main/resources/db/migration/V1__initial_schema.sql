-- BingoPlus Initial Schema (Multi-Tenant)
-- ===========================================
-- Architecture: Separate Database Per Tenant
--   Master Database (bingo_master): users + tenant_registry
--   Tenant Databases (bingo_agent_{id}): game/domain tables
-- Tables are auto-created by Hibernate (ddl-auto=update).

-- ===========================================
-- MASTER DATABASE: bingo_master
-- Contains user auth/routing + tenant registry
-- ===========================================

-- Users table (ALL users: SUPER_ADMIN, ADMIN, PLAYER)
-- Used for authentication and tenant routing
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

-- Tenant registry (maps admin_id to tenant database name)
CREATE TABLE IF NOT EXISTS tenant_registry (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT UNIQUE NOT NULL,
    database_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Invite codes (cross-tenant registration codes, stored in master DB)
CREATE TABLE IF NOT EXISTS invite_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    admin_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for master database
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_parent_id ON users(parent_id);
CREATE INDEX IF NOT EXISTS idx_tenant_registry_admin_id ON tenant_registry(admin_id);
CREATE INDEX IF NOT EXISTS idx_invite_codes_code ON invite_codes(code);

-- ===========================================
-- TENANT DATABASES: bingo_agent_{adminId}
-- Each agent gets their own database with:
-- games, cards, game_cards, called_numbers,
-- winners, transactions, audit_logs,
-- topup_requests, platform_config
-- ===========================================

-- Games table
CREATE TABLE IF NOT EXISTS games (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTRATION_OPEN',
    entry_fee DECIMAL(19,2) NOT NULL DEFAULT 0,
    max_players INTEGER NOT NULL DEFAULT 50,
    current_call_index INTEGER NOT NULL DEFAULT 0,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Bingo cards (pre-generated number grids)
CREATE TABLE IF NOT EXISTS cards (
    id BIGSERIAL PRIMARY KEY,
    numbers TEXT NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

-- Game-card join table (which player has which card in which game)
CREATE TABLE IF NOT EXISTS game_cards (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    player_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL REFERENCES cards(id),
    winner BOOLEAN NOT NULL DEFAULT FALSE
);

-- Called numbers for each game
CREATE TABLE IF NOT EXISTS called_numbers (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    number INTEGER NOT NULL,
    sequence_index INTEGER NOT NULL,
    called_at TIMESTAMP
);

-- Winners table
CREATE TABLE IF NOT EXISTS winners (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    player_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL REFERENCES cards(id),
    reward_amount DECIMAL(19,2) NOT NULL DEFAULT 0
);

-- Bingo claims audit trail
CREATE TABLE IF NOT EXISTS bingo_claims (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL,
    card_snapshot TEXT,
    called_numbers_snapshot TEXT,
    result VARCHAR(20) NOT NULL,
    reward_amount DECIMAL(19,2) DEFAULT 0,
    claimed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    validated_at TIMESTAMP
);

-- Transactions (append-only ledger)
-- References user_id to master DB users table (no FK constraint across databases)
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT,
    proof_image_file_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Audit logs (append-only)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Top-up requests
CREATE TABLE IF NOT EXISTS topup_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    proof_image_file_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- Platform configuration (per-tenant settings)
CREATE TABLE IF NOT EXISTS platform_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    updated_at TIMESTAMP
);

-- Safe ALTER TABLE migrations for existing databases created with older schema
ALTER TABLE games ADD COLUMN IF NOT EXISTS current_call_index INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cards ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE called_numbers ADD COLUMN IF NOT EXISTS sequence_index INTEGER NOT NULL DEFAULT 0;
ALTER TABLE called_numbers ALTER COLUMN called_at DROP NOT NULL;
ALTER TABLE called_numbers ALTER COLUMN called_at DROP DEFAULT;
ALTER TABLE platform_config ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Indexes for tenant databases
CREATE INDEX IF NOT EXISTS idx_games_admin_id ON games(admin_id);
CREATE INDEX IF NOT EXISTS idx_games_status ON games(status);
CREATE INDEX IF NOT EXISTS idx_game_cards_game_id ON game_cards(game_id);
CREATE INDEX IF NOT EXISTS idx_game_cards_player_id ON game_cards(player_id);
CREATE INDEX IF NOT EXISTS idx_called_numbers_game_id ON called_numbers(game_id);
CREATE INDEX IF NOT EXISTS idx_winners_game_id ON winners(game_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_topup_requests_approver ON topup_requests(approver_id);
CREATE INDEX IF NOT EXISTS idx_topup_requests_requester ON topup_requests(requester_id);
