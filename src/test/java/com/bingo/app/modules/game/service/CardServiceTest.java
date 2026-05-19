package com.bingo.app.modules.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.bingo.app.exception.PlayerActionException;
import com.bingo.app.modules.game.dto.CardResponse;
import com.bingo.app.modules.game.dto.GameCardResponse;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.wallet.service.WalletService;
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

    @Mock
    private WalletService walletService;

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
                .status(GameStatus.REGISTRATION_OPEN)
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
                () -> cardService.assignCard(10L, 5L, 30L)
        );

        assertEquals("You already have a card in the current game.", ex.getUserMessage());
    }

    @Test
    void assignCardRejectsPlayersFromAnotherAgentTree() {
        Game game = Game.builder()
                .id(10L)
                .adminId(100L)
                .status(GameStatus.REGISTRATION_OPEN)
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
                () -> cardService.assignCard(10L, 5L, 30L)
        );

        assertEquals("You can only join games created by your assigned agent.", ex.getUserMessage());
    }

    @Test
    void assignCardWithSpecificCardId() {
        Game game = Game.builder()
                .id(10L)
                .adminId(100L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(java.math.BigDecimal.ZERO)
                .build();

        User player = User.builder()
                .id(5L)
                .telegramId(123L)
                .role(Role.PLAYER)
                .parentId(100L)
                .build();

        Card availableCard = Card.builder()
                .id(40L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
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
        when(cardRepository.findById(40L)).thenReturn(Optional.of(availableCard));
        when(cardRepository.save(any(Card.class))).thenReturn(availableCard);
        when(gameCardRepository.save(any(GameCard.class))).thenReturn(savedGameCard);

        GameCardResponse actual = cardService.assignCard(10L, 5L, 40L);

        assertEquals(50L, actual.id());
        assertEquals(10L, actual.gameId());
        assertEquals(5L, actual.playerId());
        assertEquals(40L, actual.cardId());
        assertFalse(actual.winner());
        assertTrue(availableCard.isUsed());
    }

    @Test
    void assignCardRejectsAlreadyUsedCard() {
        Game game = Game.builder()
                .id(10L)
                .adminId(100L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(java.math.BigDecimal.ZERO)
                .build();

        User player = User.builder()
                .id(5L)
                .telegramId(123L)
                .role(Role.PLAYER)
                .parentId(100L)
                .build();

        Card usedCard = Card.builder()
                .id(40L)
                .numbers("1,2,3")
                .used(true)
                .build();

        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(userRepository.findById(5L)).thenReturn(Optional.of(player));
        when(gameCardRepository.findByGameIdAndPlayerId(10L, 5L)).thenReturn(List.of());
        when(cardRepository.findById(40L)).thenReturn(Optional.of(usedCard));

        PlayerActionException ex = assertThrows(
                PlayerActionException.class,
                () -> cardService.assignCard(10L, 5L, 40L)
        );

        assertEquals("This card has already been taken by another player. Please select a different card.", ex.getUserMessage());
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

        List<GameCardResponse> actual = cardService.findCardsForPlayer(5L);

        assertEquals(1, actual.size());
        assertEquals(70L, actual.get(0).id());
        assertEquals(12L, actual.get(0).gameId());
        assertEquals(5L, actual.get(0).playerId());
        assertEquals(80L, actual.get(0).cardId());
        assertFalse(actual.get(0).winner());
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

    @Test
    void findCardByIdReturnsCardResponse() {
        Card card = Card.builder()
                .id(90L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        when(cardRepository.findById(90L)).thenReturn(Optional.of(card));

        CardResponse actual = cardService.findCardById(90L);

        assertEquals(90L, actual.id());
        assertTrue(actual.used());
        assertNotNull(actual.numbers());
    }
}
