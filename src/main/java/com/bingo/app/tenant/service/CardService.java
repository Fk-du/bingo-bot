package com.bingo.app.tenant.service;

import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.CardResponse;
import com.bingo.app.tenant.dto.response.GameCardResponse;
import com.bingo.app.tenant.dto.response.PlayerCardResponse;
import com.bingo.app.tenant.entity.*;
import com.bingo.app.tenant.enums.AssignmentStatus;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.exception.PlayerActionException;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.tenant.repository.CardRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.bingo.app.tenant.repository.PlayerCardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository cardRepository;
    private final PlayerCardRepository playerCardRepository;
    private final GameCardRepository gameCardRepository;
    private final GameRepository gameRepository;
    private final PlayerService playerService;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;
    private final TenantMapper tenantMapper;

    private static final int[][] COLUMN_RANGES = {
            {1, 15},   // B
            {16, 30},  // I
            {31, 45},  // N
            {46, 60},  // G
            {61, 75}   // O
    };
    private static final int CARD_SIZE = 5;
    private static final int FREE_SPACE_ROW = 2;
    private static final int FREE_SPACE_COL = 2;

    /**
     * Generate a unique Bingo card
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public CardResponse generateUniqueCard() {
        int maxAttempts = 100;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int[][] numbers = generateCardNumbers();
            String numbersJson = toJson(numbers);
            String numbersHash = hashNumbers(numbersJson);

            // Check if card already exists
            if (cardRepository.findByNumbersHash(numbersHash).isEmpty()) {
                Card card = Card.builder()
                        .numbers(numbersJson)
                        .numbersHash(numbersHash)
                        .used(false)
                        .usageCount(0)
                        .winRate(0.0)
                        .build();
                return tenantMapper.toDto(cardRepository.save(card));
            }
        }

        throw new RuntimeException("Failed to generate unique card after " + maxAttempts + " attempts");
    }

    /**
     * Generate a pool of unique cards
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void generateCardPool(int count) {
        log.info("Generating {} unique cards", count);
        int generated = 0;

        for (int i = 0; i < count && generated < count; i++) {
            try {
                generateUniqueCard();
                generated++;
                if (generated % 100 == 0) {
                    log.info("Generated {} unique cards so far", generated);
                }
            } catch (Exception e) {
                log.warn("Failed to generate card on attempt {}: {}", i, e.getMessage());
            }
        }

        log.info("Successfully generated {} unique cards", generated);
    }

    /**
     * Generate random Bingo card numbers
     */
    private int[][] generateCardNumbers() {
        int[][] card = new int[CARD_SIZE][CARD_SIZE];
        SecureRandom random = new SecureRandom();

        for (int col = 0; col < CARD_SIZE; col++) {
            List<Integer> columnNumbers = new ArrayList<>();
            int min = COLUMN_RANGES[col][0];
            int max = COLUMN_RANGES[col][1];

            while (columnNumbers.size() < CARD_SIZE) {
                int number = min + random.nextInt(max - min + 1);
                if (!columnNumbers.contains(number)) {
                    columnNumbers.add(number);
                }
            }

            Collections.sort(columnNumbers);

            for (int row = 0; row < CARD_SIZE; row++) {
                card[row][col] = columnNumbers.get(row);
            }
        }

        // Set free space
        card[FREE_SPACE_ROW][FREE_SPACE_COL] = 0;

        return card;
    }

    /**
     * Assign a card to a player for a specific game
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public GameCardResponse assignCard(Long gameId, Long playerId) {
        // Validate game exists and is in registration phase
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new PlayerActionException("Game is not accepting registrations",
                    "This game is no longer accepting registrations.");
        }

        // Check if player already has a card for this game
        if (gameCardRepository.existsByGameIdAndPlayerId(gameId, playerId)) {
            throw new PlayerActionException("Player already has a card for this game",
                    "You are already registered for this game.");
        }

        // Enforce: player can only be in ONE active game at a time
        var activeGameCards = gameCardRepository.findByPlayerIdAndActiveGames(playerId);
        if (!activeGameCards.isEmpty()) {
            throw new PlayerActionException(
                    "Player already has a card for an active game",
                    "You are already registered for an active game. Finish it before joining another.");
        }

        // Check max players limit
        long currentPlayers = gameCardRepository.countByGameId(gameId);
        if (currentPlayers >= game.getMaxPlayers()) {
            throw new PlayerActionException("Game is full", "This game is full. Please wait for the next one.");
        }

        // Get or create player's assigned card
        PlayerCard playerCard = playerCardRepository
                .findByPlayerIdAndStatus(playerId, AssignmentStatus.ACTIVE)
                .orElseGet(() -> assignNewCardToPlayerEntity(playerId));

        // Create game card entry
        GameCard gameCard = GameCard.builder()
                .gameId(gameId)
                .playerId(playerId)
                .card(playerCard.getCard())
                .winner(false)
                .build();

        // Deduct entry fee from player balance (records BET transaction)
        if (playerService.getBalance(playerId).compareTo(game.getEntryFee()) < 0) {
            throw new WalletException("Insufficient balance",
                    "You don't have enough coins for the entry fee. Please request more coins.");
        }

        walletService.deductBet(playerId, game.getEntryFee(), gameId);

        // Update prize pool
        game.setPrizePool(game.getPrizePool().add(game.getEntryFee()));
        gameRepository.save(game);

        // Update player card stats
        playerCard.setGamesPlayed(playerCard.getGamesPlayed() + 1);
        playerCardRepository.save(playerCard);

        return tenantMapper.toDto(gameCardRepository.save(gameCard));
    }

    /**
     * Assign a new card to a player (for ongoing use)
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public PlayerCardResponse assignNewCardToPlayer(Long playerId) {
        return tenantMapper.toDto(assignNewCardToPlayerEntity(playerId));
    }

    /**
     * Get player's current active card
     */
    public PlayerCardResponse getPlayerActiveCard(Long playerId) {
        return tenantMapper.toDto(playerCardRepository.findByPlayerIdAndStatus(playerId, AssignmentStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active card found for player")));
    }

    /**
     * Get all cards for a player
     */
    public List<GameCardResponse> findCardsForPlayer(Long playerId) {
        return gameCardRepository.findByPlayerIdOrderByCreatedAtDesc(playerId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    /**
     * Check if player has a card for a game
     */
    public boolean hasCardForGame(Long gameId, Long playerId) {
        return gameCardRepository.existsByGameIdAndPlayerId(gameId, playerId);
    }

    /**
     * Count cards for a game
     */
    public int countCardsForGame(Long gameId) {
        return gameCardRepository.countByGameId(gameId);
    }

    /**
     * Mark card as winner
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void markCardAsWinner(Long gameId, Long playerId) {
        GameCard gameCard = gameCardRepository.findByGameIdAndPlayerId(gameId, playerId)
                .orElseThrow(() -> new RuntimeException("Game card not found"));

        gameCard.setWinner(true);
        gameCardRepository.save(gameCard);

        // Update player's card stats
        playerCardRepository.findByPlayerIdAndStatus(playerId, AssignmentStatus.ACTIVE)
                .ifPresent(playerCard -> {
                    playerCard.setGamesWon(playerCard.getGamesWon() + 1);
                    playerCardRepository.save(playerCard);

                    // Update card win rate
                    Card card = playerCard.getCard();
                    int played = playerCard.getGamesPlayed();
                    double winRate = played > 0
                            ? (playerCard.getGamesWon().doubleValue() / played) * 100
                            : 0.0;
                    card.setWinRate(winRate);
                    cardRepository.save(card);
                });
    }

    /**
     * Get available cards for a player
     */
    public List<CardResponse> getAvailableCards(int limit, int offset) {
        return cardRepository.findAvailableCards().stream()
                .skip(offset)
                .limit(limit)
                .map(tenantMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Count total available cards
     */
    public long countAvailableCards() {
        return cardRepository.countByUsedFalse();
    }

    /**
     * Automatically replenish card pool when it runs low.
     * Runs daily at 3 AM. Generates 50 cards if pool drops below 20.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void replenishCardPool() {
        long available = countAvailableCards();
        if (available < 20) {
            log.info("Card pool low ({} available). Generating 50 new cards.", available);
            try {
                generateCardPool(50);
            } catch (Exception e) {
                log.error("Failed to replenish card pool: {}", e.getMessage());
            }
        }
    }

    /**
     * Unassign a card from a player
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void unassignPlayerCard(Long playerId) {
        PlayerCard playerCard = playerCardRepository
                .findByPlayerIdAndStatus(playerId, AssignmentStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active card found"));

        playerCard.setStatus(AssignmentStatus.INACTIVE);
        playerCard.setUnassignedAt(LocalDateTime.now());
        playerCardRepository.save(playerCard);

        // Free the card
        Card card = playerCard.getCard();
        card.setUsed(false);
        cardRepository.save(card);
    }

    /**
     * Lock player's card for active game
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void lockPlayerCard(Long playerId) {
        playerCardRepository.lockPlayerCard(playerId);
    }

    /**
     * Unlock player's card after game
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void unlockPlayerCard(Long playerId) {
        playerCardRepository.unlockPlayerCard(playerId);
    }

    private PlayerCard assignNewCardToPlayerEntity(Long playerId) {
        // Check if player already has an active card
        playerCardRepository.findByPlayerIdAndStatus(playerId, AssignmentStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new PlayerActionException("Player already has an active card",
                            "You already have an active card.");
                });

        // Get an available card
        Card card = cardRepository.findAvailableCards().stream()
                .findFirst()
                .orElseGet(() -> {
                    // Generate inline to avoid circular call issues
                    int[][] numbers = generateCardNumbers();
                    String numbersJson = toJson(numbers);
                    String numbersHash = hashNumbers(numbersJson);
                    Card newCard = Card.builder()
                            .numbers(numbersJson)
                            .numbersHash(numbersHash)
                            .used(false)
                            .usageCount(0)
                            .winRate(0.0)
                            .build();
                    return cardRepository.save(newCard);
                });

        // Mark card as used
        card.setUsed(true);
        card.setUsageCount(card.getUsageCount() + 1);
        cardRepository.save(card);

        // Create player card assignment
        PlayerCard playerCard = PlayerCard.builder()
                .playerId(playerId)
                .card(card)
                .status(AssignmentStatus.ACTIVE)
                .gamesPlayed(0)
                .gamesWon(0)
                .assignedAt(LocalDateTime.now())
                .build();

        return playerCardRepository.save(playerCard);
    }

    private String toJson(int[][] numbers) {
        try {
            return objectMapper.writeValueAsString(numbers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize card", e);
        }
    }

    private String hashNumbers(String numbers) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(numbers.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash card", e);
        }
    }
}
