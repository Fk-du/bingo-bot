package com.bingo.app.modules.game.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

import com.bingo.app.modules.game.dto.CardResponse;
import com.bingo.app.modules.game.dto.GameCardResponse;
import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.PlayerActionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.game.entity.Card;
import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.repository.CardRepository;
import com.bingo.app.modules.game.repository.GameCardRepository;
import com.bingo.app.modules.game.repository.GameRepository;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.wallet.service.WalletService;

@Service
@RequiredArgsConstructor
public class CardService {

    static final String FREE_CENTER = "FREE";

    private final CardRepository cardRepository;
    private final GameCardRepository gameCardRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    @Value("${bingo.card-size:25}")
    private int cardSize;

    private final SecureRandom random = new SecureRandom();

    public boolean hasCardForGame(Long gameId, Long playerId) {
        return !gameCardRepository.findByGameIdAndPlayerId(gameId, playerId).isEmpty();
    }

    public List<GameCardResponse> findCardsForPlayer(Long playerId) {
        return gameCardRepository.findByPlayerId(playerId).stream()
                .map(GameCardResponse::from)
                .toList();
    }

    public int countCardsForGame(Long gameId) {
        return gameCardRepository.findByGameId(gameId).size();
    }

    public CardResponse findCardById(Long cardId) {
        return cardRepository.findById(cardId)
                .map(CardResponse::from)
                .orElseThrow(() -> new PlayerActionException("Card not found", "Your card could not be loaded."));
    }

    public Page<CardResponse> listAvailableCards(int page, int size) {
        return cardRepository.findByUsedFalse(PageRequest.of(page, size))
                .map(CardResponse::from);
    }

    @Transactional("tenantTransactionManager")
    public void removePlayerFromGame(Long gameId, Long playerId) {
        List<GameCard> gameCards = gameCardRepository.findByGameIdAndPlayerId(gameId, playerId);
        if (gameCards.isEmpty()) {
            throw new PlayerActionException("Player not in game", "Player is not registered in this game.");
        }

        for (GameCard gc : gameCards) {
            Card card = cardRepository.findById(gc.getCardId()).orElse(null);
            if (card != null) {
                card.setUsed(false);
                cardRepository.save(card);
            }
            gameCardRepository.delete(gc);
        }
    }

    public String formatCardBoard(Card card) {
        List<String> tokens = parseCardTokens(card.getNumbers());
        int dimension = (int) Math.sqrt(cardSize);

        if (dimension * dimension != cardSize || tokens.size() != cardSize) {
            return card.getNumbers();
        }

        StringBuilder board = new StringBuilder();
        board.append(" B   I   N   G   O");

        for (int row = 0; row < dimension; row++) {
            board.append("\n");
            for (int col = 0; col < dimension; col++) {
                if (col > 0) {
                    board.append(" ");
                }

                String token = tokens.get((row * dimension) + col);
                board.append(String.format("%3s", token));
            }
        }

        return board.toString();
    }

    @Transactional("tenantTransactionManager")
    public GameCardResponse assignCard(Long gameId, Long playerId, Long cardId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new PlayerActionException("Game not found", "The selected game could not be found."));

        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new PlayerActionException("Player not found", "Your account could not be found."));

        if (player.getRole() != Role.PLAYER) {
            throw new PlayerActionException("Invalid player role", "Only players can join a bingo game.");
        }

        if (player.getParentId() == null || !player.getParentId().equals(game.getAdminId())) {
            throw new PlayerActionException(
                    "Game ownership mismatch",
                    "You can only join games created by your assigned agent."
            );
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new PlayerActionException(
                    "Game is not open",
                    "This game is no longer accepting new players."
            );
        }

        if (hasCardForGame(gameId, playerId)) {
            throw new PlayerActionException(
                    "Player already has a card in the game",
                    "You already have a card in the current game."
            );
        }

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new PlayerActionException("Card not found", "The selected card could not be found."));

        if (card.isUsed()) {
            throw new PlayerActionException(
                    "Card already taken",
                    "This card has already been taken by another player. Please select a different card."
            );
        }

        if (game.getEntryFee().compareTo(BigDecimal.ZERO) > 0) {
            walletService.chargeGameEntry(playerId, game.getEntryFee());
        }

        try {
            card.setUsed(true);
            cardRepository.save(card);
        } catch (OptimisticLockingFailureException e) {
            throw new PlayerActionException(
                    "Card already taken",
                    "This card was just taken by another player. Please select a different card."
            );
        }

        GameCard gc = GameCard.builder()
                .gameId(gameId)
                .playerId(playerId)
                .cardId(card.getId())
                .winner(false)
                .build();

        return GameCardResponse.from(gameCardRepository.save(gc));
    }

    @Transactional("tenantTransactionManager")
    public GameCardResponse assignCard(Long gameId, Long playerId) {
        Card card = cardRepository.findFirstByUsedFalse()
                .orElseGet(this::createCardOnDemand);

        return assignCard(gameId, playerId, card.getId());
    }

    private Card createCardOnDemand() {
        Card card = Card.builder()
                .numbers(generateCardNumbers())
                .used(false)
                .build();

        return cardRepository.save(card);
    }

    private String generateCardNumbers() {
        int dimension = (int) Math.sqrt(cardSize);
        if (dimension != 5 || dimension * dimension != cardSize) {
            throw new IllegalStateException("Bingo cards currently require a 5x5 layout.");
        }

        List<List<Integer>> columns = new ArrayList<>();
        for (int column = 0; column < dimension; column++) {
            int rangeStart = (column * 15) + 1;
            int count = column == 2 ? dimension - 1 : dimension;
            columns.add(generateUniqueNumbersInRange(rangeStart, rangeStart + 14, count));
        }

        List<String> flattened = new ArrayList<>();
        for (int row = 0; row < dimension; row++) {
            for (int column = 0; column < dimension; column++) {
                if (row == 2 && column == 2) {
                    flattened.add(FREE_CENTER);
                    continue;
                }

                int sourceIndex = column == 2 && row > 2 ? row - 1 : row;
                flattened.add(String.valueOf(columns.get(column).get(sourceIndex)));
            }
        }

        return flattened.stream()
                .collect(Collectors.joining(","));
    }

    private List<Integer> generateUniqueNumbersInRange(int startInclusive, int endInclusive, int count) {
        List<Integer> values = new ArrayList<>();
        while (values.size() < count) {
            int value = random.nextInt(endInclusive - startInclusive + 1) + startInclusive;
            if (!values.contains(value)) {
                values.add(value);
            }
        }

        Collections.sort(values);
        return values;
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
