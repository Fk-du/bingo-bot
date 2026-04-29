package com.bingo.app.modules.game.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.bingo.app.exception.PlayerActionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.game.entity.Card;
import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.repository.CardRepository;
import com.bingo.app.modules.game.repository.GameCardRepository;

@Service
@RequiredArgsConstructor
public class CardService {

    static final String FREE_CENTER = "FREE";

    private final CardRepository cardRepository;
    private final GameCardRepository gameCardRepository;

    @Value("${bingo.card-size:25}")
    private int cardSize;

    private final Random random = new Random();

    public boolean hasCardForGame(Long gameId, Long playerId) {
        return !gameCardRepository.findByGameIdAndPlayerId(gameId, playerId).isEmpty();
    }

    public List<GameCard> findCardsForPlayer(Long playerId) {
        return gameCardRepository.findByPlayerId(playerId);
    }

    public int countCardsForGame(Long gameId) {
        return gameCardRepository.findByGameId(gameId).size();
    }

    public Card findCardById(Long cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new PlayerActionException("Card not found", "Your card could not be loaded."));
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

    @Transactional
    public GameCard assignCard(Long gameId, Long playerId) {
        if (hasCardForGame(gameId, playerId)) {
            throw new PlayerActionException(
                    "Player already has a card in the game",
                    "You already have a card in the current game."
            );
        }

        Card card = cardRepository.findFirstByUsedFalse()
                .orElseGet(this::createCardOnDemand);

        card.setUsed(true);
        cardRepository.save(card);

        GameCard gc = GameCard.builder()
                .gameId(gameId)
                .playerId(playerId)
                .cardId(card.getId())
                .winner(false)
                .build();

        return gameCardRepository.save(gc);
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
