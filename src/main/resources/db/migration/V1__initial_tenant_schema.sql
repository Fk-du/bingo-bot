-- Each tenant gets their own database with these tables

-- Players (links master users to this agent's game world)
CREATE TABLE IF NOT EXISTS players (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    agent_id BIGINT NOT NULL,
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Games table
CREATE TABLE IF NOT EXISTS games (
                                     id BIGSERIAL PRIMARY KEY,
                                     agent_id BIGINT NOT NULL,
                                     status VARCHAR(20) NOT NULL DEFAULT 'REGISTRATION_OPEN',
    entry_fee DECIMAL(19,2) NOT NULL DEFAULT 0,
    max_players INTEGER NOT NULL DEFAULT 50,
    current_call_index INTEGER NOT NULL DEFAULT 0,
    total_numbers_called INTEGER NOT NULL DEFAULT 0,
    prize_pool DECIMAL(19,2) NOT NULL DEFAULT 0,
    winning_pattern VARCHAR(50),
    call_interval INTEGER DEFAULT 5,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
    );

-- Global cards (shared across games for this tenant)
CREATE TABLE IF NOT EXISTS cards (
                                     id BIGSERIAL PRIMARY KEY,
                                     numbers TEXT NOT NULL,
                                     numbers_hash VARCHAR(64) UNIQUE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    usage_count INTEGER NOT NULL DEFAULT 0,
    win_rate DECIMAL(5,2) DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Player card assignments (which player has which card)
CREATE TABLE IF NOT EXISTS player_cards (
                                            id BIGSERIAL PRIMARY KEY,
                                            player_id BIGINT NOT NULL,
                                            card_id BIGINT NOT NULL REFERENCES cards(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    games_played INTEGER NOT NULL DEFAULT 0,
    games_won INTEGER NOT NULL DEFAULT 0,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    unassigned_at TIMESTAMP,
    UNIQUE(player_id, status)
    );

-- Game cards (which card used in which game)
CREATE TABLE IF NOT EXISTS game_cards (
                                          id BIGSERIAL PRIMARY KEY,
                                          game_id BIGINT NOT NULL REFERENCES games(id),
    player_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL REFERENCES cards(id),
    winner BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Called numbers sequence (sealed at game start)
CREATE TABLE IF NOT EXISTS called_numbers (
                                              id BIGSERIAL PRIMARY KEY,
                                              game_id BIGINT NOT NULL REFERENCES games(id),
    number INTEGER NOT NULL,
    sequence_index INTEGER NOT NULL,
    called_at TIMESTAMP,
    UNIQUE(game_id, sequence_index)
    );

-- Bingo claims
CREATE TABLE IF NOT EXISTS bingo_claims (
                                            id BIGSERIAL PRIMARY KEY,
                                            game_id BIGINT NOT NULL REFERENCES games(id),
    player_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL REFERENCES cards(id),
    card_snapshot TEXT,
    called_numbers_snapshot TEXT,
    result VARCHAR(20) NOT NULL,
    reward_amount DECIMAL(19,2),
    claimed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    validated_at TIMESTAMP
    );

-- Transactions (append-only ledger)
CREATE TABLE IF NOT EXISTS transactions (
                                            id BIGSERIAL PRIMARY KEY,
                                            user_id BIGINT NOT NULL,
                                            type VARCHAR(30) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    reference_id BIGINT,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Coin requests (top-ups)
CREATE TABLE IF NOT EXISTS coin_requests (
                                             id BIGSERIAL PRIMARY KEY,
                                             user_id BIGINT NOT NULL,
                                             amount DECIMAL(19,2) NOT NULL,
    screenshot_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT,
    approved_at TIMESTAMP,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Withdrawal requests
CREATE TABLE IF NOT EXISTS withdrawals (
                                           id BIGSERIAL PRIMARY KEY,
                                           user_id BIGINT NOT NULL,
                                           amount DECIMAL(19,2) NOT NULL,
    payout_method VARCHAR(50),
    payout_details TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_by BIGINT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Audit logs
CREATE TABLE IF NOT EXISTS audit_logs (
                                          id BIGSERIAL PRIMARY KEY,
                                          user_id BIGINT NOT NULL,
                                          action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Indexes
CREATE INDEX idx_players_user ON players(user_id);
CREATE INDEX idx_players_agent ON players(agent_id);
CREATE INDEX idx_players_parent ON players(parent_id);
CREATE INDEX idx_games_agent_status ON games(agent_id, status);
CREATE INDEX idx_cards_used ON cards(used);
CREATE INDEX idx_player_cards_player ON player_cards(player_id);
CREATE INDEX idx_game_cards_game ON game_cards(game_id);
CREATE INDEX idx_called_numbers_game ON called_numbers(game_id);
CREATE INDEX idx_transactions_user ON transactions(user_id);
CREATE INDEX idx_transactions_created ON transactions(created_at);