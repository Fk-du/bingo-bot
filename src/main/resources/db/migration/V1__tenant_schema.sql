-- BingoPlus Tenant Schema
-- Target: per-agent databases (bingo_agent_{id})
-- Tables: games, cards, game_cards, called_numbers, winners, bingo_claims, transactions, audit_logs, topup_requests, platform_config

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

CREATE TABLE IF NOT EXISTS cards (
    id BIGSERIAL PRIMARY KEY,
    numbers TEXT NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS game_cards (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    player_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL REFERENCES cards(id),
    winner BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS called_numbers (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    number INTEGER NOT NULL,
    sequence_index INTEGER NOT NULL,
    called_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS winners (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    player_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL REFERENCES cards(id),
    reward_amount DECIMAL(19,2) NOT NULL DEFAULT 0
);

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

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

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

CREATE TABLE IF NOT EXISTS platform_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE games ADD COLUMN IF NOT EXISTS current_call_index INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cards ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE called_numbers ADD COLUMN IF NOT EXISTS sequence_index INTEGER NOT NULL DEFAULT 0;
ALTER TABLE called_numbers ALTER COLUMN called_at DROP NOT NULL;
ALTER TABLE called_numbers ALTER COLUMN called_at DROP DEFAULT;
ALTER TABLE platform_config ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

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
