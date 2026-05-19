# BingoPlus — Unfinished Features & Implementation Plan

---

## P0 — CRITICAL BUGS (App doesn't work correctly)

| # | Area | Issue | Details | Fix |
|---|------|-------|---------|-----|
| 1 | **Frontend: AdminDashboard** | `startPauseOrResume` uses wrong status values | Checks `'WAITING'` and `'STARTED'` which backend **never emits** (uses `REGISTRATION_OPEN` / `IN_PROGRESS`). The `else` branch incorrectly calls `createGame` instead of `resumeGame` for paused state. | `AdminDashboard.tsx:55-66` — change status literals to `REGISTRATION_OPEN`, `IN_PROGRESS`. Add `CLAIM_PENDING` handling. Fix remaining branch to call `resumeGame`. |
| 2 | **Frontend: createGame API** | Sends query param, backend expects body | `services.ts:57` sends `?entryFee=X` but `AdminController.java:41-44` has `@RequestBody CreateGameRequest`. Request body is null → Spring deserialization failure. | `services.ts:56-58` — send `POST /admin/games/create` with JSON body `{ entryFee, maxPlayers }`. |
| 3 | **Backend: PlayerBotHandler** | Double-charge entry fee | `PlayerBotHandler.java:135-137` calls `cardService.assignCard(game.id(), user.getId())` which internally calls `walletService.chargeGameEntry()` at `CardService.java:163`, then calls it again externally. | Remove the duplicate `walletService.chargeGameEntry()` call at `PlayerBotHandler.java:136`. |
| 4 | **Backend: SuperAdminController.payWithdrawal** | Status never persisted | `SuperAdminController.java:96-105` calls `tx.setStatus(APPROVED)` on an in-memory object from `getAllTransactions().stream()` but never saves it to DB. Comment admits "no-op". | Replace entire method with a proper `walletService.approveWithdrawRequest()` call (or a new super-admin escrow release method). |
| 5 | **Frontend: GameStatus type** | Extra dead values confuse logic | Type allows `'WAITING'` and `'STARTED'` which the backend never sends. All checks against these values are dead code. | `types/index.ts:2` — remove `'WAITING'` and `'STARTED'` from `GameStatus` type. Update all references. |

---

## P1 — HIGH PRIORITY (Missing core functionality)

| # | Area | Issue | Details | Implementation |
|---|------|-------|---------|----------------|
| 6 | **Backend: Game creation** | `maxPlayers` never applied | `GameService.createGame()` (line 36) ignores `request.maxPlayers()`. The `@Value("${app.game.default-max-players:50}")` is declared but unused. | `GameService.java:36-45` — add `game.setMaxPlayers(maxPlayers != null ? maxPlayers : defaultMaxPlayers)`. |
| 7 | **Backend: CreateGameResponse** | Dead DTO with typo | `CreateGameResponse.java` has field `priseAmount` (typo: should be `prizeAmount`). Never constructed or returned anywhere. | Either delete it or wire it into `GameService.createGameWithEntryFee()` return. If not needed, delete. |
| 8 | **Frontend: SuperAdminDashboard** | 60% of UI is hardcoded mock data | Stats (`'24'` agents, `'$12,450'` revenue), "Network Health" gauge, and alert stubs for Reports/Settings are fake. Never fetches from API. | Wire to real backend endpoints: `GET /super-admin/reports`, `GET /super-admin/games/active`, `GET /super-admin/topup/pending`, `GET /super-admin/agents`, `GET /super-admin/transactions`. |
| 9 | **Frontend: CardPicker** | `selecting` state not reset on success | `CardPicker.tsx:60-70` — `setSelecting(null)` only called in `catch` block. On success, button text stays "Claiming...". | Add `setSelecting(null)` after `await onSelect(selectedId)` in the `try` block. |
| 10 | **Backend: Missing authorization checks** | No ownership validation on super-admin withdrawal pay | `SuperAdminController.payWithdrawal()` has no check that the withdrawal belongs to the super admin's agent tree. | Add service-level validation that the transaction's player is under the requesting super-admin hierarchy. |
| 11 | **Frontend: API services missing super-admin endpoints** | No frontend functions for `GET /super-admin/games/active`, `GET /super-admin/withdrawals`, `POST /super-admin/withdrawals/{id}/pay` | Backend has these but frontend can't call them. | Add to `services.ts` under `superAdminApi`. Wire to `SuperAdminDashboard`. |

---

## P2 — MEDIUM PRIORITY (Important features)

| # | Area | Issue | Details | Implementation |
|---|------|-------|---------|----------------|
| 12 | **Frontend: History & Shop buttons** | Decorative — no onClick handlers | `PlayerDashboard.tsx:125-130` — two icon buttons with no handlers. | Wire `playerApi.getHistory()` / `playerApi.buyPoints()` to these buttons, or remove them. |
| 13 | **Frontend: No loading/error boundary** | Uncaught render errors kill the whole app | No React ErrorBoundary anywhere. | Wrap `<App />` in an error boundary with a fallback UI and "Retry" button. |
| 14 | **Frontend: `alert()`/`prompt()`/`confirm()` everywhere** | Poor UX, no toast/notification system | Every user interaction uses native browser dialogs. | Build a toast notification component (or use a lightweight library) and replace all alert calls. |
| 15 | **Frontend: "New Game" button does hard reload** | `WinnerAnnouncement.tsx` uses `window.location.reload()` | Causes full page re-auth via Telegram. | Use `useGame().refresh()` or React state transition to reset game state and navigate gracefully. |
| 16 | **Backend: `@Valid` missing on request bodies** | `AdminController.createGame()` has `@RequestBody CreateGameRequest request` without `@Valid` | `CreateGameRequest` has `@NotNull` / `@DecimalMin` / `@Max` annotations that are not enforced. | Add `@Valid` annotation. |
| 17 | **Backend: Withdrawal amount validation** | No `@Min`/`@Positive` annotation on controller params | `PlayerController.withdrawRequest()` and `buyPoints()` accept `BigDecimal amount` without constraint — only validated in service layer. | Add `@RequestParam @Positive BigDecimal amount` (or use DTO). |
| 18 | **Backend: CORS wide open** | `allowedOrigins = *` in `SecurityConfig.java:57` | Should restrict to the Mini App URL in production. | Set to `${bingo.webapp.url}` via `@Value`. |
| 19 | **Frontend: WebSocket status debounce** | Every status update triggers full game data re-fetch | `App.tsx:35-42` — rapid status changes cause unnecessary API calls. | Add a 500ms debounce on `gameStatus` effect. |
| 20 | **Frontend: DashboardHeader.tsx** | Dead code — never imported | Exists in features/dashboard/ but no component references it. | Either integrate into MainLayout or delete. |

---

## P3 — LOW PRIORITY (Nice-to-have / hardening)

| # | Area | Issue | Details |
|---|------|-------|---------|
| 21 | **Backend: Bingo win patterns** | Only checks rows, columns, 2 diagonals | No support for four corners, blackout, X pattern, postage stamp, picture frame. Extend `GameEngineService.isWinningCard()`. |
| 22 | **Backend: Game auto-end** | No auto-end when numbers exhausted | `callNumber()` throws exception when all 75 numbers are called. Game stays `IN_PROGRESS`. After final number, auto-end game or notify admin. |
| 23 | **Backend: Scheduled tasks** | No `@Scheduled` jobs | No cleanup, report generation, or game auto-management. Add scheduled tasks for stale game cleanup, daily reports. |
| 24 | **Backend: Caching** | No Redis/Caffeine | All data fetched fresh from DB every request. Add Spring Cache for frequently-read data (config, card lists). |
| 25 | **Backend: Rate limiting** | No request throttling | No protection against brute force or DoS. Add Spring filter + bucket4j or similar. |
| 26 | **Backend: API docs** | No Swagger/OpenAPI | API surface not documented. Add `springdoc-openapi` dependency and annotate controllers. |
| 27 | **Backend: Production config cleanup** | SQL logging + ngrok URL hardcoded | `application.properties:33,47,87` — SQL bind trace enabled, dev ngrok URL as fallback. Disable SQL logging in production. Remove ngrok fallback. |
| 28 | **Tests: 65% service coverage gap** | 6 of 10 services have zero tests | `UserService`, `WinnerService`, `TopUpService`, `AuditService`, `PlatformConfigService`, `BingoCardImageGenerator`, all controllers, all bot handlers. Add unit tests, especially for critical paths (game lifecycle, wallet operations, auth). |

---

## IMPLEMENTATION ORDER

```
Phase 1 — Fix Critical Bugs (P0)
  #1  → Fix AdminDashboard GameStatus checks
  #2  → Fix createGame API body vs query param
  #3  → Fix double-charge in PlayerBotHandler
  #4  → Fix SuperAdminController.payWithdrawal persistence
  #5  → Clean up GameStatus type

Phase 2 — Complete Core Features (P1)
  #6  → Wire maxPlayers in GameService.createGame()
  #7  → Delete or fix CreateGameResponse
  #8  → Wire SuperAdminDashboard to real API
  #9  → Fix CardPicker selecting state
  #10 → Add ownership validation on payWithdrawal
  #11 → Add missing super-admin service functions

Phase 3 — Improve UX & Robustness (P2)
  #12 → Wire History/Shop buttons
  #13 → Add ErrorBoundary
  #14 → Replace alert/prompt with toasts
  #15 → Graceful game reset instead of reload
  #16 → Add @Valid to request bodies
  #17 → Add @Positive to amount params
  #18 → Lock down CORS origins
  #19 → Debounce WebSocket status effect
  #20 → Remove or wire DashboardHeader

Phase 4 — Polish & Harden (P3)
  #21-#28 — Patterns, auto-end, caching, rate limits, docs, config cleanup, tests
```

**Total tracked items: 28** (5 P0, 6 P1, 8 P2, 9 P3) across ~20 source files on the backend and ~12 on the frontend.
