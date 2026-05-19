package com.bingo.app.modules.game.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.bingo.app.infrastructure.tenant.TenantContext;
import com.bingo.app.modules.game.dto.WinnerResponse;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.GameProgressException;
import com.bingo.app.exception.PlayerActionException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bingo.app.modules.game.entity.BingoClaim;
import com.bingo.app.modules.game.entity.CalledNumber;
import com.bingo.app.modules.game.entity.Card;
import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.entity.Winner;
import com.bingo.app.modules.game.repository.BingoClaimRepository;
import com.bingo.app.modules.game.repository.CalledNumberRepository;
import com.bingo.app.modules.game.repository.CardRepository;
import com.bingo.app.modules.game.repository.GameCardRepository;
import com.bingo.app.modules.game.repository.GameRepository;
import com.bingo.app.modules.game.repository.WinnerRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEngineService {

    private final CalledNumberRepository calledNumberRepository;
    private final GameCardRepository gameCardRepository;
    private final CardRepository cardRepository;
    private final GameRepository gameRepository;
    private final WinnerService winnerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WinnerRepository winnerRepository;
    private final BingoClaimRepository bingoClaimRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bingo-number-caller");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();
    private final Map<Long, Object> claimLocks = new ConcurrentHashMap<>();
    private final Map<Long, String> gameTenants = new ConcurrentHashMap<>();

    @Value("${bingo.auto-call-interval-ms:5000}")
    private long autoCallInterval = 5000L;

    @Value("${bingo.number-range:75}")
    private int numberRange;

    @Value("${bingo.card-size:25}")
    private int cardSize;

    private final SecureRandom random = new SecureRandom();

    @Transactional("tenantTransactionManager")
    public void generateSequence(Long gameId) {
        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= numberRange; i++) {
            pool.add(i);
        }

        for (int i = pool.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, temp);
        }

        for (int idx = 0; idx < pool.size(); idx++) {
            CalledNumber cn = CalledNumber.builder()
                    .gameId(gameId)
                    .number(pool.get(idx))
                    .sequenceIndex(idx)
                    .calledAt(null)
                    .build();
            calledNumberRepository.save(cn);
        }

        log.info("Sealed call sequence generated for game {}: first 5 numbers = {}",
                gameId, pool.subList(0, Math.min(5, pool.size())));
    }

    @Transactional("tenantTransactionManager")
    public Integer callNumber(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameProgressException("Game not found", "The game could not be found."));
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            stopCalling(gameId);
            return null;
        }

        int currentIndex = game.getCurrentCallIndex();
        if (currentIndex >= numberRange) {
            stopCalling(gameId);
            game.setStatus(GameStatus.ENDED);
            gameRepository.save(game);
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.ENDED);
            log.info("Game {} auto-ended: all {} numbers have been called", gameId, numberRange);
            return null;
        }

        CalledNumber cn = calledNumberRepository.findByGameIdAndSequenceIndex(gameId, currentIndex)
                .orElseThrow(() -> new GameProgressException(
                        "Call sequence not found",
                        "The pre-generated call sequence is missing for this game."
                ));

        cn.setCalledAt(LocalDateTime.now());
        calledNumberRepository.save(cn);

        game.setCurrentCallIndex(currentIndex + 1);
        gameRepository.save(game);

        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/numbers", cn.getNumber());

        log.debug("Game {} called number {} (sequence index {})", gameId, cn.getNumber(), currentIndex);

        return cn.getNumber();
    }

    public void startCalling(Long gameId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.masterTenant();
        }
        gameTenants.put(gameId, tenantId);

        boolean sequenceExists = !calledNumberRepository.findByGameId(gameId).isEmpty();
        if (!sequenceExists) {
            generateSequence(gameId);
        }

        stopCalling(gameId);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    String previousTenant = TenantContext.get();
                    TenantContext.set(gameTenants.get(gameId));
                    try {
                        callNumber(gameId);
                    } catch (Exception e) {
                        log.error("Error calling number for game {}: {}", gameId, e.getMessage());
                    } finally {
                        TenantContext.set(previousTenant);
                    }
                },
                0, autoCallInterval, TimeUnit.MILLISECONDS
        );
        taskMap.put(gameId, future);
    }

    public void stopCalling(Long gameId) {
        ScheduledFuture<?> future = taskMap.remove(gameId);
        if (future != null) {
            future.cancel(false);
        }
        gameTenants.remove(gameId);
    }

    public void pauseCalling(Long gameId) {
        stopCalling(gameId);
    }

    public void resumeCalling(Long gameId) {
        startCalling(gameId);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    public List<Integer> getCalledNumbers(Long gameId) {
        return calledNumberRepository.findByGameIdAndCalledAtIsNotNullOrderBySequenceIndexAsc(gameId)
                .stream()
                .map(CalledNumber::getNumber)
                .toList();
    }

    @Transactional("tenantTransactionManager")
    public WinnerResponse claimBingo(Long gameId, Long playerId) {
        Object lock = claimLocks.computeIfAbsent(gameId, ignored -> new Object());

        synchronized (lock) {
            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new GameProgressException("Game not found", "The game could not be found."));

            if (game.getStatus() == GameStatus.ENDED) {
                throw new GameProgressException(
                        "Game already has a winner",
                        "This game already has a winner and is no longer accepting claims."
                );
            }

            if (game.getStatus() != GameStatus.IN_PROGRESS && game.getStatus() != GameStatus.CLAIM_PENDING) {
                throw new GameProgressException(
                        "Game is not started",
                        "This game is not active. Ask the admin to start the game first."
                );
            }

            if (!winnerRepository.findByGameId(gameId).isEmpty()) {
                throw new GameProgressException(
                        "Game already has a winner",
                        "This game already has a winner and is no longer accepting claims."
                );
            }

            GameCard gameCard = gameCardRepository.findByGameIdAndPlayerId(gameId, playerId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new PlayerActionException(
                            "Player has no card for the game",
                            "You do not have a card in the current started game."
                    ));

            Card card = cardRepository.findById(gameCard.getCardId())
                    .orElseThrow(() -> new PlayerActionException("Card not found", "Your card could not be loaded."));

            List<Integer> calledNumbers = getCalledNumbers(gameId);

            boolean isValid = isWinningCard(card, calledNumbers);

            BingoClaim claimRecord = BingoClaim.builder()
                    .gameId(gameId)
                    .playerId(playerId)
                    .cardId(card.getId())
                    .cardSnapshot(card.getNumbers())
                    .calledNumbersSnapshot(calledNumbers.stream().map(String::valueOf).collect(Collectors.joining(",")))
                    .result(isValid ? "PENDING" : "INVALID")
                    .rewardAmount(BigDecimal.ZERO)
                    .claimedAt(LocalDateTime.now())
                    .validatedAt(LocalDateTime.now())
                    .build();

            boolean enteringClaimWindow = game.getStatus() == GameStatus.IN_PROGRESS;
            if (enteringClaimWindow) {
                game.setStatus(GameStatus.CLAIM_PENDING);
                gameRepository.save(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.CLAIM_PENDING);
                stopCalling(gameId);
            }

            if (!isValid) {
                claimRecord.setResult("INVALID");
                bingoClaimRepository.save(claimRecord);

                game.setStatus(GameStatus.IN_PROGRESS);
                gameRepository.save(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.IN_PROGRESS);
                resumeCalling(gameId);

                throw new PlayerActionException(
                        "Card does not match a winning pattern",
                        "Your card does not have a valid bingo pattern yet."
                );
            }

            List<GameCard> winningCards = gameCardRepository.findByGameId(gameId).stream()
                    .filter(gc -> {
                        Card gcCard = cardRepository.findById(gc.getCardId()).orElse(null);
                        return gcCard != null && isWinningCard(gcCard, calledNumbers);
                    })
                    .toList();

            if (winningCards.isEmpty()) {
                claimRecord.setResult("INVALID");
                bingoClaimRepository.save(claimRecord);

                game.setStatus(GameStatus.IN_PROGRESS);
                gameRepository.save(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.IN_PROGRESS);
                resumeCalling(gameId);

                throw new PlayerActionException(
                        "Card does not match a winning pattern",
                        "Your card does not have a valid bingo pattern yet."
                );
            }

            List<Winner> winners = new ArrayList<>();
            for (GameCard winningCard : winningCards) {
                winningCard.setWinner(true);
                gameCardRepository.save(winningCard);
                winners.add(winnerService.createWinner(gameId, winningCard));
            }

            BigDecimal totalPool = game.getEntryFee().multiply(BigDecimal.valueOf(gameCardRepository.findByGameId(gameId).size()));
            winnerService.distributeRewards(gameId, totalPool, winners);

            game.setStatus(GameStatus.ENDED);
            gameRepository.save(game);

            stopCalling(gameId);

            List<WinnerResponse> winnerResponses = winners.stream()
                    .map(WinnerResponse::from)
                    .toList();

            WinnerResponse claimantWinner = winnerResponses.stream()
                    .filter(w -> playerId.equals(w.playerId()))
                    .findFirst()
                    .orElse(winnerResponses.get(0));

            claimRecord.setResult("VALID");
            claimRecord.setRewardAmount(claimantWinner.rewardAmount());
            bingoClaimRepository.save(claimRecord);

            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/winner", winnerResponses);
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.ENDED);

            return claimantWinner;
        }
    }

    public List<WinnerResponse> checkWinners(Long gameId, List<Integer> calledNumbers) {

        List<GameCard> cards = gameCardRepository.findByGameId(gameId);

        List<Winner> winners = new ArrayList<>();

        for (GameCard gc : cards) {
            Card card = cardRepository.findById(gc.getCardId()).orElse(null);

            if (card != null && isWinningCard(card, calledNumbers)) {
                gc.setWinner(true);
                gameCardRepository.save(gc);
                winners.add(winnerService.createWinner(gameId, gc));
            }
        }

        return winners.stream()
                .map(WinnerResponse::from)
                .toList();
    }

    boolean isWinningCard(Card card, List<Integer> called) {
        List<String> tokens = parseCardTokens(card.getNumbers());
        int dimension = (int) Math.sqrt(cardSize);

        if (dimension * dimension != cardSize || tokens.size() != cardSize) {
            return false;
        }

        // Row wins
        for (int row = 0; row < dimension; row++) {
            boolean rowWin = true;
            for (int col = 0; col < dimension; col++) {
                if (!isMarked(tokens, row, col, dimension, called)) {
                    rowWin = false;
                    break;
                }
            }
            if (rowWin) {
                return true;
            }
        }

        // Column wins
        for (int col = 0; col < dimension; col++) {
            boolean colWin = true;
            for (int row = 0; row < dimension; row++) {
                if (!isMarked(tokens, row, col, dimension, called)) {
                    colWin = false;
                    break;
                }
            }
            if (colWin) {
                return true;
            }
        }

        // Main diagonal (top-left to bottom-right)
        boolean diagonalWin = true;
        for (int i = 0; i < dimension; i++) {
            if (!isMarked(tokens, i, i, dimension, called)) {
                diagonalWin = false;
                break;
            }
        }
        if (diagonalWin) {
            return true;
        }

        // Reverse diagonal (top-right to bottom-left)
        boolean reverseDiagonalWin = true;
        for (int i = 0; i < dimension; i++) {
            if (!isMarked(tokens, i, dimension - 1 - i, dimension, called)) {
                reverseDiagonalWin = false;
                break;
            }
        }
        if (reverseDiagonalWin) {
            return true;
        }

        // Four corners
        if (isMarked(tokens, 0, 0, dimension, called)
                && isMarked(tokens, 0, dimension - 1, dimension, called)
                && isMarked(tokens, dimension - 1, 0, dimension, called)
                && isMarked(tokens, dimension - 1, dimension - 1, dimension, called)) {
            return true;
        }

        // X pattern: both diagonals simultaneously
        boolean xPattern = true;
        for (int i = 0; i < dimension; i++) {
            boolean leftDiag = isMarked(tokens, i, i, dimension, called);
            boolean rightDiag = isMarked(tokens, i, dimension - 1 - i, dimension, called);
            if (dimension % 2 == 1 && i == dimension / 2) {
                if (!leftDiag) {
                    xPattern = false;
                    break;
                }
            } else if (!leftDiag || !rightDiag) {
                xPattern = false;
                break;
            }
        }
        if (xPattern) {
            return true;
        }

        // Postage stamp: 2x2 blocks in each corner
        int[][] stampCorners = {{0, 0}, {0, dimension - 2}, {dimension - 2, 0}, {dimension - 2, dimension - 2}};
        for (int[] corner : stampCorners) {
            boolean stamp = true;
            for (int dr = 0; dr < 2 && stamp; dr++) {
                for (int dc = 0; dc < 2 && stamp; dc++) {
                    if (!isMarked(tokens, corner[0] + dr, corner[1] + dc, dimension, called)) {
                        stamp = false;
                    }
                }
            }
            if (stamp) {
                return true;
            }
        }

        // Picture frame: entire outer border
        boolean frame = true;
        for (int i = 0; i < dimension && frame; i++) {
            if (i == 0 || i == dimension - 1) {
                for (int j = 0; j < dimension; j++) {
                    if (!isMarked(tokens, i, j, dimension, called)) {
                        frame = false;
                        break;
                    }
                }
            } else if (!isMarked(tokens, i, 0, dimension, called)
                    || !isMarked(tokens, i, dimension - 1, dimension, called)) {
                frame = false;
            }
        }
        if (frame) {
            return true;
        }

        // Blackout: every cell marked
        boolean blackout = true;
        for (int row = 0; row < dimension && blackout; row++) {
            for (int col = 0; col < dimension && blackout; col++) {
                if (!isMarked(tokens, row, col, dimension, called)) {
                    blackout = false;
                }
            }
        }
        return blackout;
    }

    private boolean isMarked(List<String> tokens, int row, int col, int dimension, List<Integer> called) {
        if (row == dimension / 2 && col == dimension / 2) {
            return true;
        }

        String token = tokens.get((row * dimension) + col);
        if (CardService.FREE_CENTER.equalsIgnoreCase(token)) {
            return true;
        }

        try {
            return called.contains(Integer.valueOf(token));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private List<String> parseCardTokens(String rawNumbers) {
        if (rawNumbers == null || rawNumbers.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(rawNumbers.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
