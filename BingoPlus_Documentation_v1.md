# BingoPlus Technical Documentation

Version 1.0  
May 2026

## 1. Introduction

BingoPlus is a multi-tenant Telegram Bingo platform delivered as a Mini App. It lets independent agents manage their own player pools, run live Bingo games, and operate a coin economy inside Telegram without requiring a separate installed app.

The platform has three actor tiers:

- Super Admin: owns the system and is the source of truth for coins, platform configuration, fees, and commissions.
- Agent: manages a tenant, its players, and its games.
- Player: joins through an agent referral link and participates in games.

### 1.1 Platform Goals

- Let agents independently manage and monetise their own player base.
- Deliver a real-time Bingo experience inside Telegram.
- Give the super admin full platform oversight and revenue visibility.
- Preserve game integrity through server-authoritative number calling and claim validation.
- Maintain an auditable coin economy with clear deposit and withdrawal trails.

### 1.2 Scope

This document covers architecture, roles, onboarding, coin economy, game lifecycle, real-time behavior, API design, security, deployment, and glossary definitions for BingoPlus v1.0.

## 2. System Architecture

### 2.1 High-Level Overview

BingoPlus follows a client-server architecture with these layers:

- Telegram Bot: entry point for deep links, notifications, and Mini App launch.
- Next.js Mini App: player and admin dashboards, running inside Telegram.
- Spring Boot API: business logic, game engine, ledger, and RBAC enforcement.
- PostgreSQL: persistent store for users, games, transactions, cards, and audit logs.
- Redis: game state cache, session state, and pub/sub fan-out.
- Object Storage: private storage for payment screenshots.

### 2.2 Multi-Tenancy Model

Each agent is a tenant. Every agent-owned entity carries an `agent_id` foreign key. API access is always checked against the authenticated user and the requested resource before any query runs.

Players cannot move between agents. The super admin can read across tenants for oversight, but does not act as an agent.

### 2.3 Authentication

Every API request sends Telegram `initData` in the `Authorization` header.

The backend verifies the Telegram HMAC-SHA256 signature using the bot token, maps the Telegram user to a local user record, and loads role and tenant context from the database.

No separate password login is used.

## 3. Actors and Roles

### 3.1 Role Summary

- Super Admin: system owner, source of truth for coin reserves and platform-level financial settings.
- Agent: tenant admin who manages players, games, and wallet operations inside that tenant.
- Player: end user who registers for games, buys coins, and claims winnings.

### 3.2 Permission Matrix

| Action | Super Admin | Agent | Player | Notes |
|---|---:|---:|---:|---|
| Invite agents | Yes | No | No | One-time signed link per agent |
| Approve agents | Yes | No | No | |
| View all agents | Yes | No | No | |
| Invite players | No | Yes | Yes | Players may invite under the same agent |
| Run a game | No | Yes | No | One active game per agent |
| Call numbers | No | Yes | No | Server-authoritative |
| Register for game | No | No | Yes | |
| Claim Bingo | No | No | Yes | Server validates |
| Approve player deposits | No | Yes | No | |
| Approve agent deposits | Yes | No | No | Super admin is authoritative |
| Process player withdrawals | No | Yes | No | |
| Process agent withdrawals | Yes | No | No | |
| Set platform fees | Yes | No | No | |
| View platform revenue | Yes | No | No | |
| Broadcast notifications | Yes | No | No | |
| Force-end any game | Yes | No | No | Emergency override |

## 4. Onboarding Flows

### 4.1 Super Admin Registration

The system is bootstrapped with a single super admin Telegram account, configured at deployment time.

The super admin opens the bot and the platform grants system-level access after validating the Telegram user ID.

### 4.2 Agent Invitation and Registration

The super admin generates a signed invitation link from the agent dashboard.

The invited person opens the bot through that link, completes profile setup, and is reviewed by the super admin.

Approved agents receive the agent role and a unique player referral link.

### 4.3 Player Invitation and Registration

Agents share a referral link in Telegram.

The player opens the bot, completes profile setup if needed, and is permanently bound to the referring agent.

Players may also invite friends; those new players join under the same agent.

## 5. Coin Economy

The super admin is the source of truth for coin reserves, platform fee rates, commission rates, and ledger reconciliation.

All coin balances are derived from the authoritative ledger and tenant-scoped approvals. Manual coin operations may be performed by agents or the super admin, but the super admin owns the canonical reserve and fee configuration.

### 5.1 Coin Flow

| Flow | From | To | Trigger | Authorized by |
|---|---|---|---|---|
| Agent top-up | Super admin reserve | Agent balance | Agent submits payment screenshot | Super Admin |
| Player top-up | Agent balance | Player balance | Player submits payment screenshot | Agent |
| Game entry | Player balance | Prize pool | Player registers for game | System |
| Win payout | Prize pool | Player balance | Valid Bingo claim verified | System |
| Player withdrawal | Player balance | Player payout destination | Player submits request | Agent |
| Agent withdrawal | Agent balance | Agent payout destination | Agent submits request | Super Admin |
| Platform fee | Prize pool | Platform reserve | Game ends | System |
| Agent commission | Prize pool | Agent balance | Game ends | System |

### 5.2 Screenshot Review Process

- Requester uploads a payment screenshot in the Mini App.
- The screenshot is stored in private object storage.
- The request enters a pending queue for the relevant approver.
- Approver either accepts the request and credits coins atomically, or rejects it with a reason.
- The requester receives a Telegram notification of the outcome.
- Every request is logged with approver ID and timestamp.

### 5.3 Withdrawal Rules

- Player specifies amount and payout method.
- The system checks available balance, minimum withdrawal threshold, and pending game status.
- Coins are frozen until the request is resolved.
- Approved requests are paid externally and then marked complete.
- Rejected withdrawals release the frozen coins back to available balance.

### 5.4 Prize Distribution

When a game ends, the prize pool is distributed in this order:

1. Platform fee is deducted from the prize pool using the super-admin-configured fee rate.
2. Agent commission is deducted from the remaining amount using the super-admin-configured commission rate.
3. The winner share is paid from the remainder.

If multiple valid claims are resolved as simultaneous valid claims in the same decision window, the winner share is split equally among them.

`winner_payout = prize_pool * (1 - platform_fee_rate) * (1 - agent_commission_rate)`

## 6. Game Lifecycle

Each agent runs one game at a time. All number calling is server-authoritative.

### 6.1 States

| State | Description |
|---|---|
| REGISTRATION_OPEN | Agent has opened a new game and players can join. |
| IN_PROGRESS | Registration is locked and number calling has started. |
| CLAIM_PENDING | A Bingo claim is being validated and number calling is paused. |
| ENDED | Game is complete and prize distribution has been resolved. |

### 6.2 Registration Phase

- Agent sets bet amount, winning pattern, max players, cards per player, and call interval.
- Players see the lobby, current registrant count, and registered players list.
- On registration, bet coins are deducted and a unique 5x5 Bingo card is generated.
- The Bingo layout uses the standard 1-75 distribution with a free center space.
- Late registration after game start is blocked.

### 6.3 Gameplay Phase

- Agent starts the game and registration locks.
- Numbers are called at a configurable interval.
- Each called number is broadcast to all connected participants.
- Players tap called numbers for display purposes only; server state remains authoritative.
- Agent can pause or resume calling at any time.
- The dashboard shows connected players and the count of numbers called.

### 6.4 Bingo Claim and Validation

- Player taps Claim Bingo when they believe they have a winning pattern.
- The server validates the claim against the authoritative called-number list and the stored card state.
- If valid, the game transitions to ENDED and the prize is distributed.
- If invalid, the player is disqualified from that game and number calling resumes.
- If simultaneous valid claims are accepted in the same decision window, the winner share is split equally.
- The claim audit trail stores the card state, called numbers snapshot, result, and timestamp.

### 6.5 Agent Game Controls

| Control | Effect |
|---|---|
| Open Registration | Creates a game record and broadcasts REGISTRATION_OPEN |
| Start Game | Locks registration and activates number calling |
| Pause Calling | Suspends the scheduler |
| Resume Calling | Continues the scheduler |
| End Game (forced) | Ends the game without a winner and refunds bets |
| Announce Number | Re-broadcasts a number from history without drawing a new one |

## 7. Feature Specifications by Role

### 7.1 Player Dashboard

- Balance overview with available and frozen coins.
- Buy coins through screenshot upload and request submission.
- Request withdrawal with payout details.
- Transaction history with filtering and export.
- Game lobby and registration flow.
- Live Bingo card with called-number highlighting.
- Called-number board and full call history.
- Win/loss notification through the app and Telegram.
- Game history with card snapshot and result.
- Referral link and invited player count.
- Profile management.

### 7.2 Agent Dashboard

- Overview metrics.
- Player management.
- Game control panel.
- Live game monitor.
- Coin request review.
- Withdrawal processing.
- Request coins from the super admin.
- Revenue report.
- Game history.
- Referral link management.
- Notifications.
- Player suspend and reinstate actions.

### 7.3 Super Admin Dashboard

- Platform overview.
- Agent management.
- Agent coin requests.
- Agent withdrawal processing.
- Fee configuration.
- Game oversight.
- Dispute resolution.
- System configuration.
- Audit log.
- Broadcast message tools.
- Platform analytics.

## 8. Real-Time System

All real-time communication uses STOMP over SockJS.

### 8.1 WebSocket Events

| Event | Payload and Subscribers |
|---|---|
| `game.number_called` | Number, column, sequence, and called numbers - broadcast to players and agent |
| `game.state_changed` | Game ID, new state, timestamp |
| `game.bingo_claimed` | Player result and prize amount |
| `game.paused` | Pause reason |
| `game.resumed` | Empty payload |
| `game.ended` | Winner, prize amount, refund flag |
| `lobby.player_joined` | Player and card count |
| `player.balance_updated` | Available and frozen balance |
| `player.request_resolved` | Request status and reason if needed |
| `agent.request_received` | Request details for the agent |

### 8.2 Connection Lifecycle

- Client establishes WebSocket connection after authentication.
- On reconnect, the client requests the active game snapshot and re-subscribes.
- Players who reconnect mid-game receive the full called-number board.
- Heartbeats keep idle connections alive.

## 9. API Design

### 9.1 Base URL and Auth

Base URL: `https://api.bingoplus.app/v1`

Authorization header: Telegram `initData` on every request.

### 9.2 Core Endpoints

| Endpoint | Description |
|---|---|
| `POST /auth/login` | Verify `initData` and create or load the user profile |
| `GET /users/me` | Current user profile and role |
| `GET /agents` | Super admin agent listing |
| `POST /agents/invite` | Generate agent invitation link |
| `PATCH /agents/{id}/status` | Approve, suspend, or reinstate agent |
| `GET /players` | Agent player listing |
| `PATCH /players/{id}/status` | Suspend or reinstate player |
| `POST /games` | Create a new game |
| `POST /games/{id}/start` | Start number calling |
| `POST /games/{id}/pause` | Pause calling |
| `POST /games/{id}/resume` | Resume calling |
| `POST /games/{id}/end` | Force-end game |
| `GET /games/active` | Snapshot of current active game state |
| `POST /games/{id}/register` | Player game registration |
| `POST /games/{id}/claim` | Submit Bingo claim |
| `GET /games/{id}/audit` | Game audit trail |
| `POST /coins/requests` | Create coin deposit request |
| `GET /coins/requests` | List coin deposit requests |
| `PATCH /coins/requests/{id}` | Approve or reject a deposit request |
| `POST /withdrawals` | Submit withdrawal request |
| `GET /withdrawals` | List withdrawals |
| `PATCH /withdrawals/{id}/pay` | Mark withdrawal as paid |
| `POST /screenshots/upload` | Request a pre-signed screenshot upload URL |
| `GET /reports/revenue` | Revenue report |
| `GET /reports/games` | Game history report |
| `POST /broadcast` | Send Telegram notification to a target group |
| `GET /audit-log` | Platform audit log |
| `PATCH /config` | Update platform configuration |

## 10. Database Schema

Key entities:

- `users`
- `agents`
- `games`
- `bingo_cards`
- `game_registrations`
- `bingo_claims`
- `coin_requests`
- `withdrawals`
- `transactions`
- `audit_log`
- `config`

The `transactions` and `audit_log` tables are append-only.

## 11. Security

### 11.1 Authentication and Authorization

- Telegram `initData` is validated on every request.
- RBAC is enforced at the endpoint layer.
- Cross-tenant access is blocked by tenant-scoped queries.

### 11.2 Game Integrity

- Bingo cards are generated with `SecureRandom`.
- Number sequences are generated server-side and sealed at game start.
- Claim validation is performed only on the server.
- Valid simultaneous claims share the winner payout equally.

### 11.3 Financial Integrity

- Coin operations run inside database transactions.
- Ledger records are append-only.
- Withdrawal requests freeze coins until resolution.

### 11.4 Infrastructure Security

- HTTPS and WSS are enforced.
- Screenshot uploads stay in private storage.
- Signed URLs are short-lived.
- All admin actions are written to audit logs.

## 12. Non-Functional Requirements

| Requirement | Target | Measurement | Priority | Notes |
|---|---|---|---|---|
| Number-call broadcast latency | < 300ms p95 | WebSocket delta | Critical | Client receipt |
| Bingo claim validation | < 500ms | API response time | Critical | POST /claim to resolution |
| API response time | < 800ms p95 | Server response time | High | Excludes WS endpoints |
| Concurrent players per game | 500 | Load test | High | Per agent game instance |
| Platform uptime | 99.5% monthly | Monitoring | High | Excluding maintenance |
| Reconnect recovery | < 1s | Time to full board render | High | After reconnect |
| Screenshot upload size | Max 5 MB | API validation | Low | JPEG/PNG/WEBP only |
| Data retention | 12 months minimum | DB policy | Medium | Games, transactions, audit logs |

## 13. Deployment and Environment

### 13.1 Environment Variables

```env
TELEGRAM_BOT_TOKEN=<bot_token>
TELEGRAM_BOT_USERNAME=BingoPlusBot
SUPER_ADMIN_TELEGRAM_ID=<telegram_user_id>

POSTGRES_URL=jdbc:postgresql://db:5432/bingoplus
POSTGRES_USER=bingoplus
POSTGRES_PASSWORD=<secret>
REDIS_URL=redis://redis:6379

STORAGE_BUCKET=bingoplus-screenshots
STORAGE_REGION=eu-central-1
STORAGE_ACCESS_KEY=<key>
STORAGE_SECRET_KEY=<secret>
MINI_APP_URL=https://app.bingoplus.app
```

### 13.2 Docker Services

| Service | Description |
|---|---|
| `api` | Spring Boot application on port 8080 |
| `frontend` | Next.js application on port 3000 |
| `db` | PostgreSQL 15 with migrations on startup |
| `redis` | Redis 7 for session cache and pub/sub |
| `nginx` | Reverse proxy and TLS termination |

## 14. Glossary

| Term | Definition |
|---|---|
| Agent | Tenant admin who manages players and games under the platform |
| Coin | Internal platform currency |
| Frozen coins | Coins held pending withdrawal or resolution |
| `initData` | Telegram-signed payload passed to the Mini App on launch |
| CSPRNG | Cryptographically secure pseudo-random number generator |
| Prize pool | Coins collected from player bet registrations for a game |
| Line | Any complete row, column, or diagonal |
| Single line | One completed line |
| Double line | Two completed lines |
| Full house | All 24 non-free cells covered |
| STOMP | Simple Text Oriented Messaging Protocol |
| SockJS | WebSocket client with HTTP fallback |
| Deep link | Telegram bot link that encodes launch parameters |
| Audit log | Append-only record of every admin action |
| Ledger | Append-only record of every coin movement |

---

End of BingoPlus Technical Documentation v1.0
