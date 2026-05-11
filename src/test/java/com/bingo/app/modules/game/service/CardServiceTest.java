package com.bingo.app.modules.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.bingo.app.exception.PlayerActionException;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.modules.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bingo.app.modules.game.entity.Card;
import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.repository.CardRepository;
import com.bingo.app.modules.game.repository.GameCardRepository;
import com.bingo.app.modules.game.repository.GameRepository;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.game.service.CardService;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private GameCardRepository gameCardRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CardService cardService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(cardService, "cardSize", 25);
    }

    @Test
    void assignCardRejectsDuplicatePlayerCardInGame() {
        Game game = Game.builder()
                .id(10L)
                .adminId(100L)
                .status(GameStatus.WAITING)
                .build();

        User player = User.builder()
                .id(5L)
                .telegramId(123L)
                .role(Role.PLAYER)
                .parentId(100L)
                .build();

        GameCard existing = GameCard.builder()
                .id(20L)
                .gameId(10L)
                .playerId(5L)
                .cardId(30L)
                .winner(false)
                .build();

        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(userRepository.findById(5L)).thenReturn(Optional.of(player));
        when(gameCardRepository.findByGameIdAndPlayerId(10L, 5L)).thenReturn(List.of(existing));

        PlayerActionException ex = assertThrows(
                PlayerActionException.class,
                () -> cardService.assignCard(10L, 5L)
        );

        assertEquals("You already have a card in the current game.", ex.getUserMessage());
    }

    @Test
    void assignCardRejectsPlayersFromAnotherAgentTree() {
        Game game = Game.builder()
                .id(10L)
                .adminId(100L)
                .status(GameStatus.WAITING)
                .build();

        User player = User.builder()
                .id(5L)
                .telegramId(123L)
                .role(Role.PLAYER)
                .parentId(200L)
                .build();

        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(userRepository.findById(5L)).thenReturn(Optional.of(player));

        PlayerActionException ex = assertThrows(
                PlayerActionException.class,
                () -> cardService.assignCard(10L, 5L)
        );

        assertEquals("You can only join games created by your assigned agent.", ex.getUserMessage());
    }

    @Test
    void assignCardCreatesCardOnDemandWhenNoUnusedCardExists() {
        Game game = Game.builder()
                .id(10L)
                .adminId(100L)
                .status(GameStatus.WAITING)
                .build();

        User player = User.builder()
                .id(5L)
                .telegramId(123L)
                .role(Role.PLAYER)
                .parentId(100L)
                .build();

        Card newCard = Card.builder()
                .id(40L)
                .numbers("1,2,3")
                .used(false)
                .build();

        GameCard savedGameCard = GameCard.builder()
                .id(50L)
                .gameId(10L)
                .playerId(5L)
                .cardId(40L)
                .winner(false)
                .build();

        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(userRepository.findById(5L)).thenReturn(Optional.of(player));
        when(gameCardRepository.findByGameIdAndPlayerId(10L, 5L)).thenReturn(List.of());
        when(cardRepository.findFirstByUsedFalse()).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenReturn(newCard);
        when(gameCardRepository.save(any(GameCard.class))).thenReturn(savedGameCard);

        GameCard actual = cardService.assignCard(10L, 5L);

        assertEquals(savedGameCard, actual);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository, times(2)).save(captor.capture());
        String generatedNumbers = captor.getAllValues().get(0).getNumbers();
        assertNotNull(generatedNumbers);
        assertValidBingoCard(generatedNumbers);
        assertFalse(captor.getAllValues().get(0).isUsed());
        assertEquals(40L, captor.getAllValues().get(1).getId());
    }

    @Test
    void findCardsForPlayerReturnsStoredAssignments() {
        GameCard existing = GameCard.builder()
                .id(70L)
                .gameId(12L)
                .playerId(5L)
                .cardId(80L)
                .winner(false)
                .build();

        when(gameCardRepository.findByPlayerId(5L)).thenReturn(List.of(existing));

        List<GameCard> actual = cardService.findCardsForPlayer(5L);

        assertEquals(1, actual.size());
        assertSame(existing, actual.get(0));
    }

    @Test
    void formatCardBoardRendersReadableGrid() {
        Card card = Card.builder()
                .id(90L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        String actual = cardService.formatCardBoard(card);

        assertTrue(actual.startsWith(" B   I   N   G   O"));
        assertTrue(actual.contains("FREE"));
        assertTrue(actual.contains("\n  1  16  31  46  61"));
    }

    private void assertValidBingoCard(String rawNumbers) {
        List<String> tokens = java.util.Arrays.stream(rawNumbers.split(","))
                .map(String::trim)
                .toList();

        assertEquals(25, tokens.size());
        assertEquals(CardService.FREE_CENTER, tokens.get(12));

        for (int row = 0; row < 5; row++) {
            assertInRange(tokens.get((row * 5)), 1, 15);
            assertInRange(tokens.get((row * 5) + 1), 16, 30);
            if (row != 2) {
                assertInRange(tokens.get((row * 5) + 2), 31, 45);
            }
            assertInRange(tokens.get((row * 5) + 3), 46, 60);
            assertInRange(tokens.get((row * 5) + 4), 61, 75);
        }
    }

    private void assertInRange(String value, int min, int max) {
        int number = Integer.parseInt(value);
        org.junit.jupiter.api.Assertions.assertTrue(number >= min && number <= max);
    }
}
