package com.bingo.app.modules.game.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.GameProgressException;
import com.bingo.app.exception.PlayerActionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.game.entity.CalledNumber;
import com.bingo.app.modules.game.entity.Card;
import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.entity.Winner;
import com.bingo.app.modules.game.repository.CalledNumberRepository;
import com.bingo.app.modules.game.repository.CardRepository;
import com.bingo.app.modules.game.repository.GameCardRepository;
import com.bingo.app.modules.game.repository.GameRepository;
import com.bingo.app.modules.game.repository.WinnerRepository;

@Service
@RequiredArgsConstructor
public class GameEngineService {

    private final CalledNumberRepository calledNumberRepository;
    private final GameCardRepository gameCardRepository;
    private final CardRepository cardRepository;
    private final GameRepository gameRepository;
    private final WinnerService winnerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WinnerRepository winnerRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bingo-number-caller");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();
    private final Map<Long, Object> claimLocks = new ConcurrentHashMap<>();

    @Value("${bingo.auto-call-interval-ms:5000}")
    private long autoCallInterval = 5000L;

    @Value("${bingo.number-range:75}")
    private int numberRange;

    @Value("${bingo.card-size:25}")
    private int cardSize;

    private final Random random = new Random();

    public Integer callNumber(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameProgressException("Game not found", "The game could not be found."));
        if (game.getStatus() != GameStatus.STARTED) {
            stopCalling(gameId);
            return null;
        }

        List<Integer> alreadyCalled = calledNumberRepository.findByGameId(gameId)
                .stream()
                .map(CalledNumber::getNumber)
                .toList();

        List<Integer> remainingNumbers = IntStream.rangeClosed(1, numberRange)
                .boxed()
                .filter(number -> !alreadyCalled.contains(number))
                .collect(Collectors.toCollection(ArrayList::new));

        if (remainingNumbers.isEmpty()) {
            stopCalling(gameId);
            throw new GameProgressException(
                    "All bingo numbers have already been called for this game.",
                    "All bingo numbers have already been called for this game."
            );
        }

        int number = remainingNumbers.get(random.nextInt(remainingNumbers.size()));

        CalledNumber cn = CalledNumber.builder()
                .gameId(gameId)
                .number(number)
                .calledAt(LocalDateTime.now())
                .build();

        calledNumberRepository.save(cn);

        // Broadcast to WebSocket
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/numbers", number);

        return number;
    }

    public void startCalling(Long gameId) {
        stopCalling(gameId); // Clean up any existing task
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        callNumber(gameId);
                    } catch (Exception e) {
                        // Log error or handle
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
    }

    public void pauseCalling(Long gameId) {
        stopCalling(gameId);
    }

    public void resumeCalling(Long gameId) {
        startCalling(gameId);
    }

    public List<Integer> getCalledNumbers(Long gameId) {
        return calledNumberRepository.findByGameIdOrderByCalledAtAsc(gameId)
                .stream()
                .map(CalledNumber::getNumber)
                .toList();
    }

    public Winner claimBingo(Long gameId, Long playerId) {
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

            if (game.getStatus() != GameStatus.STARTED && game.getStatus() != GameStatus.CLAIM_PENDING) {
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

            boolean enteringClaimWindow = game.getStatus() == GameStatus.STARTED;
            if (enteringClaimWindow) {
                game.setStatus(GameStatus.CLAIM_PENDING);
                gameRepository.save(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.CLAIM_PENDING);
                stopCalling(gameId);
            }

            List<Integer> calledNumbers = getCalledNumbers(gameId);
            if (!isWinningCard(card, calledNumbers)) {
                game.setStatus(GameStatus.STARTED);
                gameRepository.save(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.STARTED);
                resumeCalling(gameId);

                throw new PlayerActionException(
                        "Card does not match a winning pattern",
                        "Your card does not have a valid bingo pattern yet."
                );
            }

            List<GameCard> winningCards = gameCardRepository.findByGameId(gameId).stream()
                    .filter(gc -> {
                        Card gameCardEntity = cardRepository.findById(gc.getCardId()).orElse(null);
                        return gameCardEntity != null && isWinningCard(gameCardEntity, calledNumbers);
                    })
                    .toList();

            if (winningCards.isEmpty()) {
                game.setStatus(GameStatus.STARTED);
                gameRepository.save(game);
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.STARTED);
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

            Winner claimantWinner = winners.stream()
                    .filter(winner -> playerId.equals(winner.getPlayerId()))
                    .findFirst()
                    .orElse(winners.get(0));

            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/winner", winners);
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.ENDED);

            return claimantWinner;
        }
    }

    public List<Winner> checkWinners(Long gameId, List<Integer> calledNumbers) {

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

        return winners;
    }

    boolean isWinningCard(Card card, List<Integer> called) {
        List<String> tokens = parseCardTokens(card.getNumbers());
        int dimension = (int) Math.sqrt(cardSize);

        if (dimension * dimension != cardSize || tokens.size() != cardSize) {
            return false;
        }

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

        boolean reverseDiagonalWin = true;
        for (int i = 0; i < dimension; i++) {
            if (!isMarked(tokens, i, dimension - 1 - i, dimension, called)) {
                reverseDiagonalWin = false;
                break;
            }
        }

        return reverseDiagonalWin;
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
