package com.bingo.app.tenant.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.master.entity.TenantRegistry;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.TenantRegistryRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.BingoClaimResponse;
import com.bingo.app.tenant.entity.*;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.exception.RequestAlreadyProcessedException;
import com.bingo.app.tenant.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameEngineService {

    private final GameRepository gameRepository;
    private final CalledNumberRepository calledNumberRepository;
    private final GameCardRepository gameCardRepository;
    private final BingoClaimRepository bingoClaimRepository;
    private final WalletService walletService;
    private final CardService cardService;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Qualifier("tenantTransactionTemplate")
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final TenantMapper tenantMapper;
    private final TenantRegistryRepository tenantRegistryRepository;
    private final UserRepository userRepository;


    @Value("${bingo.fees.admin-commission-percent:10}")
    private BigDecimal defaultAdminCommissionPercent;

    @Value("${bingo.game.claim-timeout-seconds:300}")
    private int claimTimeoutSeconds;

    // Track active game schedulers
    private final ScheduledThreadPoolExecutor taskScheduler = new ScheduledThreadPoolExecutor(4);
    private final Map<Long, ScheduledFuture<?>> activeGameTasks = new ConcurrentHashMap<>();
    private final Map<Long, String> gameTenantContexts = new ConcurrentHashMap<>();

    // Bingo patterns
    private static final List<int[]> WINNING_PATTERNS = List.of(            new int[]{0,0, 0,1, 0,2, 0,3, 0,4}, // Top row
            new int[]{1,0, 1,1, 1,2, 1,3, 1,4}, // Second row
            new int[]{2,0, 2,1, 2,2, 2,3, 2,4}, // Third row
            new int[]{3,0, 3,1, 3,2, 3,3, 3,4}, // Fourth row
            new int[]{4,0, 4,1, 4,2, 4,3, 4,4}, // Bottom row
            new int[]{0,0, 1,0, 2,0, 3,0, 4,0}, // First column
            new int[]{0,1, 1,1, 2,1, 3,1, 4,1}, // Second column
            new int[]{0,2, 1,2, 2,2, 3,2, 4,2}, // Third column
            new int[]{0,3, 1,3, 2,3, 3,3, 4,3}, // Fourth column
            new int[]{0,4, 1,4, 2,4, 3,4, 4,4}, // Fifth column
            new int[]{0,0, 1,1, 2,2, 3,3, 4,4}, // Main diagonal
            new int[]{0,4, 1,3, 2,2, 3,1, 4,0}  // Anti-diagonal
    );

    /**
     * Start automatic number calling for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void startCalling(Long gameId) {
        startCallingInternal(gameId);
    }

    /**
     * Announce the STARTING countdown and schedule the transition to IN_PROGRESS
     * (with number calling) once the countdown elapses.
     */
    public void scheduleGameStart(Long gameId, int countdownSeconds) {
        String tenantId = TenantContext.getTenant();
        Game game = gameRepository.findById(gameId).orElse(null);
        java.time.LocalDateTime startTime = game != null ? game.getStartTime() : java.time.LocalDateTime.now().plusSeconds(countdownSeconds);
        publishGameStatusEvent(gameId, GameStatus.STARTING, startTime);
        taskScheduler.schedule(() -> {
            TenantContext.setTenant(tenantId);
            try {
                transactionTemplate.execute(status -> {
                    beginCallingAfterCountdown(gameId);
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to start game {} after countdown: {}", gameId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }, Math.max(0, countdownSeconds), java.util.concurrent.TimeUnit.SECONDS);
        log.info("Game {} starting in {} seconds", gameId, countdownSeconds);
    }

    private void beginCallingAfterCountdown(Long gameId) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) return;
        if (game.getStatus() == GameStatus.STARTING) {
            game.setStatus(GameStatus.IN_PROGRESS);
            game.setStartTime(LocalDateTime.now());
            gameRepository.save(game);
        }
        if (game.getStatus() == GameStatus.IN_PROGRESS) {
            startCallingInternal(gameId);
        }
    }

    private void startCallingInternal(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        stopCalling(gameId);

        String tenantId = TenantContext.getTenant();
        gameTenantContexts.put(gameId, tenantId);

        int interval = game.getCallInterval() != null ? game.getCallInterval() : 5;

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
            TenantContext.setTenant(tenantId);
            try {
                transactionTemplate.execute(status -> {
                    callNextNumber(gameId);
                    return null;
                });
            } catch (Exception e) {
                log.error("Error calling number for game {}: {}", gameId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }, 0, interval, java.util.concurrent.TimeUnit.SECONDS);

        activeGameTasks.put(gameId, future);

        publishGameStatusEvent(gameId, GameStatus.IN_PROGRESS);

        log.info("Started automatic number calling for game {} every {} seconds", gameId, interval);
    }

    /**
     * Recover active game schedulers after server restart.
     * Iterates over all registered tenants and restarts schedulers for IN_PROGRESS games.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverActiveGames() {
        log.info("Recovering active game schedulers...");

        List<TenantRegistry> tenants;
        try {
            tenants = tenantRegistryRepository.findAll();
        } catch (Exception e) {
            log.warn("Could not load tenant registry for recovery: {}", e.getMessage());
            return;
        }

        int recovered = 0;
        for (TenantRegistry tenant : tenants) {
            String tenantId = "agent_" + tenant.getAdminUserId();
            try {
                TenantContext.setTenant(tenantId);
                List<Game> activeGames = gameRepository.findByStatus(GameStatus.IN_PROGRESS);
                for (Game game : activeGames) {
                    try {
                        startCalling(game.getId());
                        recovered++;
                        log.info("Recovered scheduler for game {} (tenant {})", game.getId(), tenantId);
                    } catch (Exception e) {
                        log.error("Failed to recover scheduler for game {}: {}", game.getId(), e.getMessage());
                    }
                }
                // Resume games that were mid-countdown when the server restarted
                List<Game> startingGames = gameRepository.findByStatus(GameStatus.STARTING);
                for (Game game : startingGames) {
                    try {
                        long delayMs = game.getStartTime() == null ? 0
                                : java.time.Duration.between(LocalDateTime.now(), game.getStartTime()).toMillis();
                        if (delayMs <= 0) {
                            transactionTemplate.execute(status -> {
                                beginCallingAfterCountdown(game.getId());
                                return null;
                            });
                        } else {
                            scheduleGameStart(game.getId(), (int) Math.ceil(delayMs / 1000.0));
                        }
                        recovered++;
                        log.info("Resumed countdown/start for game {} (tenant {})", game.getId(), tenantId);
                    } catch (Exception e) {
                        log.error("Failed to resume starting game {}: {}", game.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to query games for tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }

        log.info("Active game recovery complete. Recovered {} game(s).", recovered);
    }

    /**
     * Auto-reject claims that have been pending longer than the configured timeout.
     * Runs every 30 seconds across all tenants.
     */
    @Scheduled(fixedDelay = 30_000)
    public void enforceClaimTimeout() {
        List<TenantRegistry> tenants;
        try {
            tenants = tenantRegistryRepository.findAll();
        } catch (Exception e) {
            return;
        }

        for (TenantRegistry tenant : tenants) {
            String tenantId = "agent_" + tenant.getAdminUserId();
            try {
                TenantContext.setTenant(tenantId);
                List<Game> pendingGames = gameRepository.findByStatus(GameStatus.CLAIM_PENDING);
                for (Game game : pendingGames) {
                    try {
                        processClaimTimeout(game);
                    } catch (Exception e) {
                        log.error("Error processing claim timeout for game {}: {}", game.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Error checking claim timeouts for tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void processClaimTimeout(Game game) {
        List<BingoClaim> pendingClaims = bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(game.getId(), "VALID");

        if (pendingClaims.isEmpty()) {
            return;
        }

        BingoClaim oldestClaim = pendingClaims.get(0);
        if (oldestClaim.getClaimedAt() == null) {
            return;
        }

        Duration elapsed = Duration.between(oldestClaim.getClaimedAt(), LocalDateTime.now());
        if (elapsed.getSeconds() < claimTimeoutSeconds) {
            return;
        }

        log.warn("Game {}: Claim timeout reached ({}s elapsed). Auto-rejecting {} pending claim(s).",
                game.getId(), elapsed.getSeconds(), pendingClaims.size());

        for (BingoClaim claim : pendingClaims) {
            claim.setResult("REJECTED");
            claim.setValidatedAt(LocalDateTime.now());
            claim.setRejectionReason("Claim timed out after " + claimTimeoutSeconds + " seconds");
            bingoClaimRepository.save(claim);
        }

        game.setStatus(GameStatus.IN_PROGRESS);
        gameRepository.save(game);
        startCalling(game.getId());
        publishClaimResolvedEvent(game.getId(), GameStatus.IN_PROGRESS);

        log.info("Game {}: All timed-out claims rejected, game resumed.", game.getId());
    }

    /**
     * Stop automatic number calling for a game
     */
    public void stopCalling(Long gameId) {
        ScheduledFuture<?> future = activeGameTasks.remove(gameId);
        if (future != null) {
            future.cancel(false);
            log.info("Stopped number calling for game {}", gameId);
        }
        gameTenantContexts.remove(gameId);
    }

    /**
     * Wrapper method for calling next number (compatible with AdminBotHandler)
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public Integer callNumber(Long gameId) {
        return callNextNumber(gameId);
    }

    /**
     * Call the next number in the sequence
     */
    public Integer callNextNumber(Long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            log.debug("Game {} is not in progress, stopping caller", gameId);
            stopCalling(gameId);
            return null;
        }

        int currentIndex = game.getCurrentCallIndex();

        // Check if game has ended (all 75 numbers called)
        if (currentIndex >= 75) {
            endGameWithoutWinner(gameId, "All numbers called, no winner");
            return null;
        }

        // Get next number from sealed sequence
        CalledNumber nextNumber = calledNumberRepository
                .findByGameIdAndSequenceIndex(gameId, currentIndex)
                .orElseThrow(() -> new RuntimeException("Number sequence not found"));

        // Update game progress
        game.setCurrentCallIndex(currentIndex + 1);
        game.setTotalNumbersCalled(game.getTotalNumbersCalled() + 1);

        // Mark as called
        nextNumber.setCalledAt(LocalDateTime.now());
        calledNumberRepository.save(nextNumber);
        gameRepository.save(game);

        // Publish WebSocket events
        publishNumberCalledEvent(gameId, nextNumber);

        log.info("Game {}: Called number {} (sequence {})", gameId, nextNumber.getNumber(), currentIndex);

        return nextNumber.getNumber();
    }

    /**
     * Call a specific number (manual override)
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public Integer callSpecificNumber(Long gameId, Integer number) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        // Find if this number has been called yet
        Optional<CalledNumber> existing = calledNumberRepository
                .findByGameIdAndNumberAndCalledAtIsNotNull(gameId, number);

        if (existing.isPresent()) {
            throw new RuntimeException("Number already called");
        }

        // Find the number in sequence and mark it as called
        CalledNumber calledNumber = calledNumberRepository
                .findByGameIdAndNumber(gameId, number)
                .orElseThrow(() -> new RuntimeException("Number not in sequence"));

        calledNumber.setCalledAt(LocalDateTime.now());
        calledNumberRepository.save(calledNumber);

        // Update game progress if this is the next number
        if (calledNumber.getSequenceIndex() == game.getCurrentCallIndex()) {
            game.setCurrentCallIndex(game.getCurrentCallIndex() + 1);
            game.setTotalNumbersCalled(game.getTotalNumbersCalled() + 1);
            gameRepository.save(game);
        }

        publishNumberCalledEvent(gameId, calledNumber);

        log.info("Game {}: Manually called number {}", gameId, number);
        return number;
    }

    /**
     * Claim Bingo for a player — allows multiple simultaneous claims.
     * No server-side validation/ban occurs here; validity is determined by the
     * admin during claim review (approve/reject).
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BingoClaimResult claimBingo(Long gameId, Long playerId, java.util.List<Integer> markedNumbers, Boolean autoMark) throws JsonProcessingException {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS && game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new GameProgressException("Game is not accepting claims",
                    "Bingo can't be claimed right now.");
        }

        if (bingoClaimRepository.existsByGameIdAndPlayerIdAndResult(gameId, playerId, "VALID")) {
            throw new RequestAlreadyProcessedException("Player already claimed Bingo in game " + gameId);
        }

        // Get player's card for this game
        GameCard gameCard = gameCardRepository.findByGameIdAndPlayerId(gameId, playerId)
                .orElseThrow(() -> new RuntimeException("Player not registered for this game"));

        if (gameCard.isBanned()) {
            throw new GameProgressException("Player banned from game " + gameId,
                    "You have been banned from this game.");
        }

        // Get called numbers so far
        List<Integer> calledNumbers = calledNumberRepository
                .findCalledNumbersByGameId(gameId);

        // Get card numbers
        Card card = gameCard.getCard();

        // Persist the player's auto-mark preference if sent with the claim.
        if (autoMark != null) {
            gameCard.setAutoMark(autoMark);
            gameCardRepository.save(gameCard);
        }

        // No server-side validation here — determining whether the claim is a valid
        // Bingo is the admin's responsibility via claim review (approve/reject).

        // Pause on first claim only
        boolean firstClaim = game.getStatus() == GameStatus.IN_PROGRESS;
        if (firstClaim) {
            stopCalling(gameId);
            game.setStatus(GameStatus.CLAIM_PENDING);
            gameRepository.save(game);
            publishGameStatusEvent(gameId, GameStatus.CLAIM_PENDING);
        }

        BingoClaim claim = BingoClaim.builder()
                .gameId(gameId)
                .playerId(playerId)
                .cardId(card.getId())
                .cardSnapshot(card.getNumbers())
                .calledNumbersSnapshot(objectMapper.writeValueAsString(calledNumbers))
                .result("VALID")
                .claimedAt(LocalDateTime.now())
                .build();
        bingoClaimRepository.save(claim);

        publishClaimPendingEvent(gameId, claim);

        log.info("Game {}: Bingo claimed by player {} (claimId={}), first={}",
                gameId, playerId, claim.getId(), firstClaim);

        return BingoClaimResult.builder()
                .valid(true)
                .claimId(claim.getId())
                .pendingReview(true)
                .rewardAmount(BigDecimal.ZERO)
                .build();
    }

    /**
     * Approve ALL pending claims as simultaneous winners (same call state).
     * Up to {@link #MAX_SIMULTANEOUS_WINNERS} players share the net pool
     * equally; the game ends and every winner's card is marked.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BingoClaimResult approveAllClaims(Long gameId, Long adminId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminId)) {
            throw new GameProgressException("Game does not belong to this admin",
                    "This game does not belong to you.");
        }

        List<BingoClaim> pending = bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (pending.isEmpty()) {
            throw new GameProgressException("No pending claims for game " + gameId,
                    "There are no claims waiting for review.");
        }
        if (pending.size() > MAX_SIMULTANEOUS_WINNERS) {
            throw new GameProgressException(
                    "Too many simultaneous winners: " + pending.size(),
                    pending.size() + " players claimed at once. Approving is only allowed for up to "
                            + MAX_SIMULTANEOUS_WINNERS + " winners — restart the game instead.");
        }

        // Atomically lock every claim — any that lose a race are dropped
        LocalDateTime now = LocalDateTime.now();
        List<BingoClaim> winners = new java.util.ArrayList<>();
        for (BingoClaim c : pending) {
            if (bingoClaimRepository.claimForProcessing(c.getId(), adminId, now) == 1) {
                winners.add(c);
            }
        }
        int shareCount = winners.size();

        BigDecimal prizePool = game.getPrizePool();
        BigDecimal adminCommission = commissionFor(game);
        BigDecimal netPool = prizePool.subtract(adminCommission);
        walletService.creditAgentCommission(game.getAdminUserId(), adminCommission, gameId);

        BigDecimal[] shares = splitEvenly(netPool, shareCount);
        for (int i = 0; i < shareCount; i++) {
            BingoClaim winner = winners.get(i);
            winner.setRewardAmount(shares[i]);
            bingoClaimRepository.save(winner);
            walletService.creditWinnings(winner.getPlayerId(), shares[i], gameId);
            gameCardRepository.findByGameIdAndPlayerId(gameId, winner.getPlayerId()).ifPresent(gc -> {
                gc.setWinner(true);
                gameCardRepository.save(gc);
            });
            cardService.markCardAsWinner(gameId, winner.getPlayerId());
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);
        stopCalling(gameId);
        publishGameStatusEvent(gameId, GameStatus.ENDED);

        log.info("Game {}: Admin {} approved {} simultaneous winners, each paid {}. Game ended.",
                gameId, adminId, shareCount, shares[0]);

        return BingoClaimResult.builder()
                .valid(true)
                .pendingReview(false)
                .gameEnded(true)
                .approvedCount(shareCount)
                .rewardAmount(shares[0])
                .commission(adminCommission)
                .build();
    }

    /** Exact cent-perfect even split; earlier winners absorb the rounding remainder. */
    BigDecimal[] splitEvenly(BigDecimal total, int n) {
        long cents = total.movePointRight(2).setScale(0, java.math.RoundingMode.DOWN).longValueExact();
        long base = cents / n;
        long remainder = cents % n;
        BigDecimal[] parts = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            parts[i] = BigDecimal.valueOf(base + (i < remainder ? 1 : 0), 2);
        }
        return parts;
    }

    /**
     * Reject a pending Bingo claim — game resumes only when no claims remain
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void rejectClaim(Long gameId, Long claimId, Long adminId, String reason) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new RequestAlreadyProcessedException("Game is not in CLAIM_PENDING state");
        }

        BingoClaim claim = bingoClaimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));

        if (!claim.getGameId().equals(gameId)) {
            throw new RuntimeException("Claim does not belong to this game");
        }

        // Atomically claim — concurrent approvals/rejections lose here
        LocalDateTime rejectedAt = LocalDateTime.now();
        int claimed = bingoClaimRepository.claimForProcessing(claimId, adminId, rejectedAt);
        if (claimed == 0) {
            throw new RequestAlreadyProcessedException("Claim already processed");
        }
        claim.setValidatedBy(adminId);
        claim.setValidatedAt(rejectedAt);

        // Mark claim as rejected
        claim.setResult("REJECTED");
        claim.setRejectionReason(reason);
        bingoClaimRepository.save(claim);

        // Resume game only if no other valid pending claims
        long remaining = bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (remaining == 0) {
            game.setStatus(GameStatus.IN_PROGRESS);
            gameRepository.save(game);
            startCalling(gameId);
            publishClaimResolvedEvent(gameId, GameStatus.IN_PROGRESS);
            log.info("Game {}: All claims resolved, game resumed.", gameId);
        } else {
            publishClaimResolvedEvent(gameId, GameStatus.CLAIM_PENDING);
        }

        log.info("Game {}: Admin {} rejected claim {}. Reason: {}.",
                gameId, adminId, claimId, reason);
    }

    /**
     * End game without a winner (force end or no claims)
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void endGameWithoutWinner(Long gameId, String reason) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        stopCalling(gameId);

        // Refund entry fees — but only if nobody won (winners already got prizes;
        // refunding them too would double-pay once approval-resume is in play)
        boolean hasWinner = !gameCardRepository.findByGameIdAndWinnerTrue(gameId).isEmpty();
        if (hasWinner) {
            // Winner already took the pot (minus commission); losers' fees stay with the house.
            log.info("Game {}: skipping entry-fee refunds because the game has a winner.", gameId);
        } else {
            refundAllPlayersForGame(gameId, game.getEntryFee());
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

        publishGameStatusEvent(gameId, GameStatus.ENDED);

        log.info("Game {} ended without winner. Reason: {}", gameId, reason);
    }

    /** Admin commission for a game: per-game percentage, falling back to the configured default. */
    private BigDecimal commissionFor(Game game) {
        BigDecimal pct = game.getCommissionPercent() != null
                ? game.getCommissionPercent() : defaultAdminCommissionPercent;
        return game.getPrizePool().multiply(pct)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }


    /**
     * Validate Bingo claim — respects the game's configured winning pattern.
     *
     * SINGLE_LINE: any one of the 12 line patterns (rows, columns, diagonals) is complete.
     * DOUBLE_LINE: at least two distinct line patterns are complete.
     * TRIPLE_LINE: at least three distinct line patterns are complete.
     * FULL_HOUSE:  all 24 non-free cells are called.
     * FOUR_CORNERS: the four corner cells are called.
     * BLACKOUT: same as FULL_HOUSE (alias).
     * L_SHAPE: bottom row + first column complete.
     * T_SHAPE: top row + third column complete.
     * X_SHAPE: both diagonals complete.
     * POSTAGE_STAMP: any 2x2 block in a corner is complete.
     * PLUS: middle row + middle column complete.
     * FRAME: all outer border cells complete.
     * DIAMOND: the four cells diagonally adjacent to the centre are complete.
     * Z_SHAPE: top row + main diagonal + bottom row complete.
     */
    boolean validateBingo(int[][] cardNumbers, List<Integer> calledNumbers, String pattern) {
        return validateBingo(cardNumbers, calledNumbers, pattern, null);
    }

    boolean validateBingo(int[][] cardNumbers, List<Integer> calledNumbers, String pattern, String customCellsJson) {
        Set<Integer> calledSet = new HashSet<>(calledNumbers);
        calledSet.add(0);

        if ("CUSTOM".equals(pattern)) {
            List<int[]> cells = parseCustomCells(customCellsJson);
            if (cells.isEmpty()) {
                return false;
            }
            for (int[] cell : cells) {
                int r = cell[0], c = cell[1];
                if (r == 2 && c == 2) continue; // free centre
                if (!calledSet.contains(cardNumbers[r][c])) {
                    return false;
                }
            }
            return true;
        }

        if ("FULL_HOUSE".equals(pattern) || "BLACKOUT".equals(pattern)) {
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 5; col++) {
                    if (row == 2 && col == 2) continue;
                    if (!calledSet.contains(cardNumbers[row][col])) {
                        return false;
                    }
                }
            }
            return true;
        }

        if ("FOUR_CORNERS".equals(pattern)) {
            return calledSet.contains(cardNumbers[0][0])
                    && calledSet.contains(cardNumbers[0][4])
                    && calledSet.contains(cardNumbers[4][0])
                    && calledSet.contains(cardNumbers[4][4]);
        }

        if ("X_SHAPE".equals(pattern)) {
            // Both diagonals
            boolean mainDiag = true, antiDiag = true;
            for (int i = 0; i < 5; i++) {
                if (!calledSet.contains(cardNumbers[i][i])) mainDiag = false;
                if (!calledSet.contains(cardNumbers[i][4 - i])) antiDiag = false;
            }
            return mainDiag && antiDiag;
        }

        if ("L_SHAPE".equals(pattern)) {
            // Bottom row (4,0-4) + first column (0-4,0) — center counted once
            boolean bottomRow = true, firstCol = true;
            for (int col = 0; col < 5; col++) {
                if (!calledSet.contains(cardNumbers[4][col])) bottomRow = false;
            }
            for (int row = 0; row < 5; row++) {
                if (!calledSet.contains(cardNumbers[row][0])) firstCol = false;
            }
            return bottomRow && firstCol;
        }

        if ("T_SHAPE".equals(pattern)) {
            // Top row (0,0-4) + third column (0-4,2)
            boolean topRow = true, thirdCol = true;
            for (int col = 0; col < 5; col++) {
                if (!calledSet.contains(cardNumbers[0][col])) topRow = false;
            }
            for (int row = 0; row < 5; row++) {
                if (!calledSet.contains(cardNumbers[row][2])) thirdCol = false;
            }
            return topRow && thirdCol;
        }

        if ("POSTAGE_STAMP".equals(pattern)) {
            // Any 2x2 block in the four corners
            return is2x2Complete(cardNumbers, calledSet, 0, 0)
                    || is2x2Complete(cardNumbers, calledSet, 0, 3)
                    || is2x2Complete(cardNumbers, calledSet, 3, 0)
                    || is2x2Complete(cardNumbers, calledSet, 3, 3);
        }

        if ("PLUS".equals(pattern)) {
            // Middle row (2,0-4) + middle column (0-4,2)
            boolean middleRow = true, middleCol = true;
            for (int col = 0; col < 5; col++) {
                if (!calledSet.contains(cardNumbers[2][col])) middleRow = false;
            }
            for (int row = 0; row < 5; row++) {
                if (!calledSet.contains(cardNumbers[row][2])) middleCol = false;
            }
            return middleRow && middleCol;
        }

        if ("FRAME".equals(pattern)) {
            // All outer border cells
            int[][] frame = {
                    {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4},
                    {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4},
                    {1, 0}, {2, 0}, {3, 0},
                    {1, 4}, {2, 4}, {3, 4}
            };
            for (int[] cell : frame) {
                if (!calledSet.contains(cardNumbers[cell[0]][cell[1]])) {
                    return false;
                }
            }
            return true;
        }

        if ("DIAMOND".equals(pattern)) {
            // The four cells diagonally adjacent to the (free) center
            return calledSet.contains(cardNumbers[1][1])
                    && calledSet.contains(cardNumbers[1][3])
                    && calledSet.contains(cardNumbers[3][1])
                    && calledSet.contains(cardNumbers[3][3]);
        }

        if ("Z_SHAPE".equals(pattern)) {
            // Top row + main diagonal + bottom row
            boolean topRow = true, bottomRow = true, diag = true;
            for (int col = 0; col < 5; col++) {
                if (!calledSet.contains(cardNumbers[0][col])) topRow = false;
                if (!calledSet.contains(cardNumbers[4][col])) bottomRow = false;
            }
            for (int i = 1; i < 4; i++) {
                if (!calledSet.contains(cardNumbers[i][i])) diag = false;
            }
            return topRow && bottomRow && diag;
        }

        int completedLines = 0;
        for (int[] winPattern : WINNING_PATTERNS) {
            boolean lineComplete = true;
            for (int i = 0; i < winPattern.length; i += 2) {
                int row = winPattern[i];
                int col = winPattern[i + 1];
                if (!calledSet.contains(cardNumbers[row][col])) {
                    lineComplete = false;
                    break;
                }
            }
            if (lineComplete) {
                completedLines++;
            }
        }

        return switch (pattern != null ? pattern : "SINGLE_LINE") {
            case "DOUBLE_LINE" -> completedLines >= 2;
            case "TRIPLE_LINE" -> completedLines >= 3;
            default -> completedLines >= 1;
        };
    }

    private boolean is2x2Complete(int[][] card, Set<Integer> called, int startRow, int startCol) {
        return called.contains(card[startRow][startCol])
                && called.contains(card[startRow][startCol + 1])
                && called.contains(card[startRow + 1][startCol])
                && called.contains(card[startRow + 1][startCol + 1]);
    }

    /**
     * Parse card numbers from JSON
     */
    private int[][] parseCardNumbers(String numbersJson) {
        try {
            return objectMapper.readValue(numbersJson, int[][].class);
        } catch (Exception e) {
            log.error("Failed to parse card numbers: {}", e.getMessage());
            return new int[5][5];
        }
    }

    /**
     * Parse a custom pattern's [[row,col],...] cells from JSON.
     */
    private List<int[]> parseCustomCells(String cellsJson) {
        if (cellsJson == null || cellsJson.trim().isEmpty()) {
            return List.of();
        }
        try {
            int[][] cells = objectMapper.readValue(cellsJson, int[][].class);
            if (cells == null) return List.of();
            java.util.List<int[]> result = new java.util.ArrayList<>();
            for (int[] cell : cells) {
                if (cell.length == 2 && cell[0] >= 0 && cell[0] < 5 && cell[1] >= 0 && cell[1] < 5) {
                    result.add(cell);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse custom pattern cells: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get current game state for a player
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public GameState getGameState(Long gameId, Long playerId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        List<Integer> calledNumbers = calledNumberRepository
                .findCalledNumbersByGameId(gameId);

        GameCard gameCard = gameCardRepository
                .findByGameIdAndPlayerId(gameId, playerId)
                .orElse(null);

        int[][] cardNumbers = null;
        if (gameCard != null) {
            cardNumbers = parseCardNumbers(gameCard.getCard().getNumbers());
        }

        return GameState.builder()
                .gameId(gameId)
                .status(game.getStatus())
                .currentCallIndex(game.getCurrentCallIndex())
                .totalNumbersCalled(game.getTotalNumbersCalled())
                .calledNumbers(calledNumbers)
                .prizePool(game.getPrizePool())
                .playerCard(cardNumbers)
                .hasPlayerCard(gameCard != null)
                .isWinner(gameCard != null && gameCard.isWinner())
                .isBanned(gameCard != null && gameCard.isBanned())
                .autoMark(Boolean.TRUE.equals(
                        (gameCard != null && gameCard.getAutoMark() != null)
                                ? gameCard.getAutoMark()
                                : game.getAutoMark()))
                .commissionPercent(game.getCommissionPercent())
                .markedNumbers(parseMarkedNumbers(gameCard))
                .startTime(game.getStartTime())
                .winningPattern(game.getWinningPattern())
                .customPatternName(game.getCustomPatternName())
                .customPatternCells(game.getCustomPatternCells())
                .fairnessHash(game.getFairnessHash())
                .build();
    }

    /**
     * Get admin game state (game metadata + called numbers + player count)
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public AdminGameState getAdminGameState(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        List<Integer> calledNumbers = calledNumberRepository
                .findCalledNumbersByGameId(gameId);

        int playerCount = gameCardRepository.countByGameId(gameId);

        return AdminGameState.builder()
                .game(game)
                .calledNumbers(calledNumbers)
                .playerCount(playerCount)
                .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class AdminGameState {
        private Game game;
        private List<Integer> calledNumbers;
        private int playerCount;
    }

    static java.util.List<Integer> parseMarkedNumbers(GameCard gameCard) {
        if (gameCard == null || gameCard.getMarkedNumbers() == null
                || gameCard.getMarkedNumbers().isBlank()) {
            return java.util.List.of();
        }
        try {
            return java.util.Arrays.stream(gameCard.getMarkedNumbers().split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
        } catch (NumberFormatException e) {
            return java.util.List.of();
        }
    }

    /**
     * Persist the player's manual daubs so they survive refreshes and reconnects.
     * Every mark must correspond to an already-called number (or the free spot).
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void saveMarks(Long gameId, Long playerId, java.util.List<Integer> markedNumbers, Boolean autoMark) {
        if (markedNumbers == null) {
            markedNumbers = java.util.List.of();
        }
        GameCard gameCard = gameCardRepository.findByGameIdAndPlayerId(gameId, playerId)
                .orElseThrow(() -> new RuntimeException("Player not registered for this game"));

        // Persist the player's auto-mark preference (null = follow the game default).
        if (autoMark != null) {
            gameCard.setAutoMark(autoMark);
            gameCardRepository.save(gameCard);
        }

        boolean effectiveAutoMark = Boolean.TRUE.equals(gameCard.getAutoMark() != null
                ? gameCard.getAutoMark()
                : gameRepository.findById(gameId).map(Game::getAutoMark).orElse(true));

        if (effectiveAutoMark) {
            throw new GameProgressException("Game uses auto-marking",
                    "Your card is set to auto-mark numbers.");
        }

        java.util.Set<Integer> allowed = new java.util.HashSet<>(
                calledNumberRepository.findCalledNumbersByGameId(gameId));
        allowed.add(0);
        for (Integer n : markedNumbers) {
            if (n == null || !allowed.contains(n)) {
                throw new GameProgressException("Number not called yet",
                        (n == null ? "A mark" : n) + " hasn't been called yet.");
            }
        }

        gameCard.setMarkedNumbers(markedNumbers.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
        gameCardRepository.save(gameCard);
    }

    /**
     * Get all called numbers for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<Integer> getCalledNumbers(Long gameId) {
        return calledNumberRepository.findCalledNumbersByGameId(gameId);
    }

    /**
     * Check if a number has been called
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean isNumberCalled(Long gameId, Integer number) {
        return calledNumberRepository.existsByGameIdAndNumberAndCalledAtIsNotNull(gameId, number);
    }

    /**
     * Pause number calling
     */
    public void pauseGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        stopCalling(gameId);
        game.setStatus(GameStatus.PAUSED);
        gameRepository.save(game);

        publishGameStatusEvent(gameId, GameStatus.PAUSED);

        log.info("Game {} paused", gameId);
    }

    /**
     * Get all pending (unresolved) valid claims for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<BingoClaimResponse> getPendingClaims(Long gameId) {
        return bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID")
                .stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    /**
     * Get the pending (unresolved) winning claims' card snapshots, so players other than
     * the claimants can inspect whether each card actually completed the game.
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<com.bingo.app.tenant.dto.response.PendingClaimCardResponse> getPendingClaimCards(Long gameId) {
        return bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID")
                .stream()
                .map(claim -> {
                    String name = "Player #" + claim.getPlayerId();
                    if (claim.getPlayerId() != null) {
                        name = userRepository.findById(claim.getPlayerId())
                                .map(this::displayName)
                                .orElse(name);
                    }
                    return com.bingo.app.tenant.dto.response.PendingClaimCardResponse.builder()
                            .claimId(claim.getId())
                            .playerId(claim.getPlayerId())
                            .playerName(name)
                            .cardNumbers(parseCardNumbers(claim.getCardSnapshot()))
                            .calledNumbers(calledNumberRepository.findCalledNumbersByGameId(gameId))
                            .build();
                })
                .toList();
    }

    private String displayName(User user) {
        String full = String.join(" ",
                        user.getFirstName() == null ? "" : user.getFirstName(),
                        user.getLastName() == null ? "" : user.getLastName())
                .trim();
        return full.isEmpty() ? (user.getUsername() != null ? user.getUsername() : "Player") : full;
    }

    /**
     * Resume number calling (with 5-second countdown)
     */
    public void resumeGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.PAUSED) {
            throw new RuntimeException("Game is not paused");
        }

        int countdownSeconds = 5;
        game.setStatus(GameStatus.STARTING);
        game.setStartTime(LocalDateTime.now().plusSeconds(countdownSeconds));
        gameRepository.save(game);

        String tenantId = TenantContext.getTenant();
        publishGameStatusEvent(gameId, GameStatus.STARTING, game.getStartTime());

        taskScheduler.schedule(() -> {
            TenantContext.setTenant(tenantId);
            try {
                transactionTemplate.execute(status -> {
                    Game g = gameRepository.findById(gameId).orElse(null);
                    if (g == null) return null;
                    if (g.getStatus() == GameStatus.STARTING) {
                        g.setStatus(GameStatus.IN_PROGRESS);
                        g.setStartTime(LocalDateTime.now());
                        gameRepository.save(g);
                    }
                    if (g.getStatus() == GameStatus.IN_PROGRESS) {
                        startCallingInternal(gameId);
                    }
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to resume game {} after countdown: {}", gameId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }, Math.max(0, countdownSeconds), java.util.concurrent.TimeUnit.SECONDS);

        log.info("Game {} resuming in {} seconds", gameId, countdownSeconds);
    }


    // Maximum simultaneous winners who may share the pot
    private static final int MAX_SIMULTANEOUS_WINNERS = 3;

    // WebSocket event publishers
    private void publishEvent(Long gameId, String type, ObjectNode data) {
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("type", type);
            event.set("data", data);
            String payload = objectMapper.writeValueAsString(event);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, payload);
        } catch (Exception e) {
            log.error("Failed to publish WebSocket event for game {}: {}", gameId, e.getMessage());
        }
    }

    private void publishNumberCalledEvent(Long gameId, CalledNumber calledNumber) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", calledNumber.getId());
        data.put("gameId", calledNumber.getGameId());
        data.put("number", calledNumber.getNumber());
        data.put("sequenceIndex", calledNumber.getSequenceIndex());
        publishEvent(gameId, "NUMBER_CALLED", data);
    }

    private void publishGameStatusEvent(Long gameId, GameStatus status) {
        publishGameStatusEvent(gameId, status, null);
    }

    private void publishGameStatusEvent(Long gameId, GameStatus status, java.time.LocalDateTime startTime) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("status", status.name());
        if (startTime != null) {
            data.put("startTime", startTime.toString());
        }
        publishEvent(gameId, "GAME_STATUS_CHANGED", data);
    }

    private void publishClaimPendingEvent(Long gameId, BingoClaim claim) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("id", claim.getId());
            data.put("gameId", claim.getGameId());
            data.put("playerId", claim.getPlayerId());
            data.put("cardId", claim.getCardId());
            data.put("cardSnapshot", claim.getCardSnapshot());
            data.put("calledNumbersSnapshot", claim.getCalledNumbersSnapshot());
            data.put("result", claim.getResult());
            publishEvent(gameId, "CLAIM_PENDING", data);
        } catch (Exception e) {
            log.error("Failed to publish CLAIM_PENDING event: {}", e.getMessage());
        }
    }

    private void publishClaimResolvedEvent(Long gameId, GameStatus status) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("status", status.name());
        publishEvent(gameId, "CLAIM_RESOLVED", data);
    }

    /**
     * Refund entry fees to all registered players for a game with no winners.
     */
    private void refundAllPlayersForGame(Long gameId, BigDecimal entryFee) {
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<GameCard> allCards = gameCardRepository.findByGameId(gameId);
        for (GameCard card : allCards) {
            walletService.refundPlayer(card.getPlayerId(), entryFee, gameId);
        }

        log.info("Game {}: Refunded entry fee {} to {} players", gameId, entryFee, allCards.size());
    }

    @lombok.Builder
    @lombok.Data
    public static class BingoClaimResult {
        private boolean valid;
        private Long claimId;
        private boolean pendingReview;
        private boolean gameEnded;
        private int approvedCount;
        private BigDecimal rewardAmount;
        private BigDecimal commission;
        private boolean banned;
    }

    @lombok.Builder
    @lombok.Data
    public static class GameState {
        private Long gameId;
        private GameStatus status;
        private Integer currentCallIndex;
        private Integer totalNumbersCalled;
        private List<Integer> calledNumbers;
        private BigDecimal prizePool;
        private int[][] playerCard;
        private boolean hasPlayerCard;
        private boolean isWinner;
        private boolean isBanned;
        private Boolean autoMark;
        private BigDecimal commissionPercent;
        private java.util.List<Integer> markedNumbers;
        private java.time.LocalDateTime startTime;
        private String winningPattern;
        private String customPatternName;
        private String customPatternCells;
        private String fairnessHash;
    }
}
