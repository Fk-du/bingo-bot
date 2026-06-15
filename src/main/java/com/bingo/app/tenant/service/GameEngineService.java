package com.bingo.app.tenant.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.tenant.entity.*;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final TransactionTemplate transactionTemplate;

    @Value("${bingo.fees.platform-fee-rate:0.10}")
    private BigDecimal platformFeeRate;

    @Value("${bingo.fees.agent-commission-rate:0.05}")
    private BigDecimal agentCommissionRate;

    // Track active game schedulers
    private final ScheduledThreadPoolExecutor taskScheduler = new ScheduledThreadPoolExecutor(4);
    private final Map<Long, ScheduledFuture<?>> activeGameTasks = new ConcurrentHashMap<>();
    private final Map<Long, String> gameTenantContexts = new ConcurrentHashMap<>();

    // Bingo patterns
    private static final List<int[]> WINNING_PATTERNS = List.of(
            new int[]{0,0, 0,1, 0,2, 0,3, 0,4}, // Top row
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
    @Transactional
    public void startCalling(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        // Cancel existing task if any
        stopCalling(gameId);

        // Capture the current tenant context (Timer/Scheduler runs in a different thread)
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

        log.info("Started automatic number calling for game {} every {} seconds", gameId, interval);
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
    @Transactional
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

        log.info("Game {}: Called number {} (sequence {})", gameId, nextNumber.getNumber(), currentIndex);

        return nextNumber.getNumber();
    }

    /**
     * Call a specific number (manual override)
     */
    @Transactional
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

        log.info("Game {}: Manually called number {}", gameId, number);
        return number;
    }

    /**
     * Claim Bingo for a player — allows multiple simultaneous claims
     */
    @Transactional
    public BingoClaimResult claimBingo(Long gameId, Long playerId) throws JsonProcessingException {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS && game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new RuntimeException("Game is not accepting claims");
        }

        if (bingoClaimRepository.existsByGameIdAndPlayerIdAndResult(gameId, playerId, "VALID")) {
            throw new RuntimeException("You have already claimed Bingo in this game");
        }

        // Get player's card for this game
        GameCard gameCard = gameCardRepository.findByGameIdAndPlayerId(gameId, playerId)
                .orElseThrow(() -> new RuntimeException("Player not registered for this game"));

        // Get called numbers so far
        List<Integer> calledNumbers = calledNumberRepository
                .findCalledNumbersByGameId(gameId);

        // Get card numbers
        Card card = gameCard.getCard();
        int[][] cardNumbers = parseCardNumbers(card.getNumbers());

        // Validate Bingo server-side
        boolean isValid = validateBingo(cardNumbers, calledNumbers, game.getWinningPattern());

        if (!isValid) {
            bingoClaimRepository.save(BingoClaim.builder()
                    .gameId(gameId)
                    .playerId(playerId)
                    .cardId(card.getId())
                    .cardSnapshot(card.getNumbers())
                    .calledNumbersSnapshot(objectMapper.writeValueAsString(calledNumbers))
                    .result("INVALID")
                    .rewardAmount(BigDecimal.ZERO)
                    .validatedAt(LocalDateTime.now())
                    .claimedAt(LocalDateTime.now())
                    .build());

            log.warn("Invalid Bingo claim from player {} in game {}", playerId, gameId);
            throw new RuntimeException("Invalid Bingo claim — no matching pattern found");
        }

        // Pause on first claim only
        boolean firstClaim = game.getStatus() == GameStatus.IN_PROGRESS;
        if (firstClaim) {
            stopCalling(gameId);
            game.setStatus(GameStatus.CLAIM_PENDING);
            gameRepository.save(game);
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
     * Approve a pending Bingo claim — pays the winner, allows up to MAX_WINNERS per game
     */
    @Transactional
    public BingoClaimResult approveClaim(Long gameId, Long claimId, Long adminId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new RuntimeException("Game is not in CLAIM_PENDING state");
        }

        BingoClaim claim = bingoClaimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));

        if (!claim.getGameId().equals(gameId)) {
            throw new RuntimeException("Claim does not belong to this game");
        }

        if (!"VALID".equals(claim.getResult())) {
            throw new RuntimeException("Only valid claims can be approved");
        }

        if (claim.getValidatedAt() != null) {
            throw new RuntimeException("Claim already processed");
        }

        Long winnerId = claim.getPlayerId();

        // Calculate prize distribution (each winner gets full prize minus fees)
        BigDecimal prizePool = game.getPrizePool();
        BigDecimal platformFee = prizePool.multiply(platformFeeRate);
        BigDecimal afterFee = prizePool.subtract(platformFee);
        BigDecimal agentCommission = afterFee.multiply(agentCommissionRate);
        BigDecimal winnerAmount = afterFee.subtract(agentCommission);

        // Mark winner card
        GameCard winnerCard = gameCardRepository.findByGameIdAndPlayerId(gameId, winnerId)
                .orElseThrow(() -> new RuntimeException("Winner card not found"));
        winnerCard.setWinner(true);
        gameCardRepository.save(winnerCard);

        // Credit winnings to player
        walletService.creditWinnings(winnerId, winnerAmount, gameId);

        // Deduct platform fee from agent
        walletService.deductPlatformFee(game.getAdminUserId(), platformFee, gameId);

        // Update approved claim
        claim.setRewardAmount(winnerAmount);
        claim.setValidatedBy(adminId);
        claim.setValidatedAt(LocalDateTime.now());
        bingoClaimRepository.save(claim);

        // Update player's card stats
        cardService.markCardAsWinner(gameId, winnerId);

        // Count how many winners so far
        long approvedCount = bingoClaimRepository
                .countByGameIdAndResultAndValidatedAtIsNotNull(gameId, "VALID");
        boolean gameEnded = false;

        if (approvedCount >= MAX_WINNERS) {
            // Max winners reached — end the game
            game.setStatus(GameStatus.ENDED);
            game.setEndTime(LocalDateTime.now());
            gameRepository.save(game);
            stopCalling(gameId);

            // Auto-reject remaining pending claims
            List<BingoClaim> remaining = bingoClaimRepository
                    .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
            for (BingoClaim other : remaining) {
                other.setResult("REJECTED");
                other.setValidatedBy(adminId);
                other.setValidatedAt(LocalDateTime.now());
                other.setRejectionReason("Max winners reached");
                bingoClaimRepository.save(other);
            }
            gameEnded = true;

            log.info("Game {}: Max winners ({}) reached. Game ended.", gameId, MAX_WINNERS);
        }

        log.info("Game {}: Admin {} approved claim {}. Winner: {} (winner #{}/{}), Prize: {}",
                gameId, adminId, claimId, winnerId, approvedCount, MAX_WINNERS, winnerAmount);

        return BingoClaimResult.builder()
                .valid(true)
                .claimId(claimId)
                .pendingReview(false)
                .gameEnded(gameEnded)
                .approvedCount((int) approvedCount)
                .rewardAmount(winnerAmount)
                .platformFee(platformFee)
                .agentCommission(agentCommission)
                .build();
    }

    /**
     * Reject a pending Bingo claim — game resumes only when no claims remain
     */
    @Transactional
    public void rejectClaim(Long gameId, Long claimId, Long adminId, String reason) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new RuntimeException("Game is not in CLAIM_PENDING state");
        }

        BingoClaim claim = bingoClaimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));

        if (!claim.getGameId().equals(gameId)) {
            throw new RuntimeException("Claim does not belong to this game");
        }

        if (claim.getValidatedAt() != null) {
            throw new RuntimeException("Claim already processed");
        }

        // Mark claim as rejected
        claim.setResult("REJECTED");
        claim.setValidatedBy(adminId);
        claim.setValidatedAt(LocalDateTime.now());
        claim.setRejectionReason(reason);
        bingoClaimRepository.save(claim);

        // Resume game only if no other valid pending claims
        long remaining = bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (remaining == 0) {
            game.setStatus(GameStatus.IN_PROGRESS);
            gameRepository.save(game);
            startCalling(gameId);
            log.info("Game {}: All claims resolved, game resumed.", gameId);
        }

        log.info("Game {}: Admin {} rejected claim {}. Reason: {}.",
                gameId, adminId, claimId, reason);
    }

    /**
     * End game without a winner (force end or no claims)
     */
    @Transactional
    public void endGameWithoutWinner(Long gameId, String reason) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        stopCalling(gameId);

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

        // Refund all players? (Optional - depends on business rules)
        // For now, no refunds

        log.info("Game {} ended without winner. Reason: {}", gameId, reason);
    }

    /**
     * Validate Bingo claim
     */
    private boolean validateBingo(int[][] cardNumbers, List<Integer> calledNumbers, String pattern) {
        // Create a set of called numbers for O(1) lookup
        Set<Integer> calledSet = new HashSet<>(calledNumbers);

        // Mark free space as always called
        calledSet.add(0);

        // Check all winning patterns
        for (int[] winPattern : WINNING_PATTERNS) {
            boolean patternComplete = true;
            for (int i = 0; i < winPattern.length; i += 2) {
                int row = winPattern[i];
                int col = winPattern[i + 1];
                int number = cardNumbers[row][col];

                if (!calledSet.contains(number)) {
                    patternComplete = false;
                    break;
                }
            }

            if (patternComplete) {
                return true;
            }
        }

        return false;
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
     * Get current game state for a player
     */
    @Transactional(readOnly = true)
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
                .build();
    }

    /**
     * Get all called numbers for a game
     */
    @Transactional(readOnly = true)
    public List<Integer> getCalledNumbers(Long gameId) {
        return calledNumberRepository.findCalledNumbersByGameId(gameId);
    }

    /**
     * Check if a number has been called
     */
    @Transactional(readOnly = true)
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
        game.setStatus(GameStatus.CLAIM_PENDING);
        gameRepository.save(game);

        log.info("Game {} paused", gameId);
    }

    /**
     * Get all pending (unresolved) valid claims for a game
     */
    @Transactional(readOnly = true)
    public List<BingoClaim> getPendingClaims(Long gameId) {
        return bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
    }

    /**
     * Resume number calling
     */
    public void resumeGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new RuntimeException("Game is not paused");
        }

        game.setStatus(GameStatus.IN_PROGRESS);
        gameRepository.save(game);

        startCalling(gameId);

        log.info("Game {} resumed", gameId);
    }

    private static final int MAX_WINNERS = 3;

    // Inner classes
    @lombok.Builder
    @lombok.Data
    public static class BingoClaimResult {
        private boolean valid;
        private Long claimId;
        private boolean pendingReview;
        private boolean gameEnded;
        private int approvedCount;
        private BigDecimal rewardAmount;
        private BigDecimal platformFee;
        private BigDecimal agentCommission;
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
    }
}