package com.bingo.app.modules.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.GameProgressException;
import com.bingo.app.exception.PlayerActionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.bingo.app.modules.game.service.GameEngineService;
import com.bingo.app.modules.game.service.WinnerService;

@ExtendWith(MockitoExtension.class)
class GameEngineServiceTest {

    @Mock
    private CalledNumberRepository calledNumberRepository;

    @Mock
    private GameCardRepository gameCardRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private WinnerRepository winnerRepository;

    @Mock
    private WinnerService winnerService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameEngineService gameEngineService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gameEngineService, "numberRange", 75);
        ReflectionTestUtils.setField(gameEngineService, "cardSize", 25);
    }

    @Test
    void callNumberUsesOnlyRemainingNumbers() {
        Game game = Game.builder()
                .id(10L)
                .adminId(2L)
                .status(GameStatus.STARTED)
                .entryFee(BigDecimal.TEN)
                .maxPlayers(10)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        List<CalledNumber> called = java.util.stream.IntStream.rangeClosed(1, 74)
                .mapToObj(number -> CalledNumber.builder()
                        .id((long) number)
                        .gameId(10L)
                        .number(number)
                        .calledAt(LocalDateTime.now())
                        .build())
                .toList();

        when(gameRepository.findById(10L)).thenReturn(java.util.Optional.of(game));
        when(calledNumberRepository.findByGameId(10L)).thenReturn(called);

        Integer actual = gameEngineService.callNumber(10L);

        assertEquals(75, actual);
        verify(calledNumberRepository).save(any(CalledNumber.class));
    }

    @Test
    void callNumberRejectsWhenAllNumbersAreExhausted() {
        Game game = Game.builder()
                .id(10L)
                .adminId(2L)
                .status(GameStatus.STARTED)
                .entryFee(BigDecimal.TEN)
                .maxPlayers(10)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        List<CalledNumber> called = java.util.stream.IntStream.rangeClosed(1, 75)
                .mapToObj(number -> CalledNumber.builder()
                        .id((long) number)
                        .gameId(10L)
                        .number(number)
                        .calledAt(LocalDateTime.now())
                        .build())
                .toList();

        when(gameRepository.findById(10L)).thenReturn(java.util.Optional.of(game));
        when(calledNumberRepository.findByGameId(10L)).thenReturn(called);

        GameProgressException ex = assertThrows(
                GameProgressException.class,
                () -> gameEngineService.callNumber(10L)
        );

        assertEquals("All bingo numbers have already been called for this game.", ex.getUserMessage());
    }

    @Test
    void claimBingoRejectsNonWinningCard() {
        Game startedGame = Game.builder()
                .id(10L)
                .adminId(2L)
                .status(GameStatus.STARTED)
                .entryFee(BigDecimal.TEN)
                .maxPlayers(10)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        GameCard gameCard = GameCard.builder()
                .id(20L)
                .gameId(10L)
                .playerId(5L)
                .cardId(30L)
                .winner(false)
                .build();

        Card card = Card.builder()
                .id(30L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        when(gameRepository.findById(10L)).thenReturn(java.util.Optional.of(startedGame));
        when(winnerRepository.findByGameId(10L)).thenReturn(List.of());
        when(gameCardRepository.findByGameIdAndPlayerId(10L, 5L)).thenReturn(List.of(gameCard));
        when(cardRepository.findById(30L)).thenReturn(java.util.Optional.of(card));
        when(calledNumberRepository.findByGameIdOrderByCalledAtAsc(10L)).thenReturn(List.of(
                CalledNumber.builder().id(1L).gameId(10L).number(1).calledAt(LocalDateTime.now()).build(),
                CalledNumber.builder().id(2L).gameId(10L).number(7).calledAt(LocalDateTime.now()).build(),
                CalledNumber.builder().id(3L).gameId(10L).number(14).calledAt(LocalDateTime.now()).build()
        ));

        PlayerActionException ex = assertThrows(
                PlayerActionException.class,
                () -> gameEngineService.claimBingo(10L, 5L)
        );

        assertEquals("Your card does not have a valid bingo pattern yet.", ex.getUserMessage());
    }

    @Test
    void claimBingoCreatesWinnerPaysPoolAndEndsGame() {
        Game startedGame = Game.builder()
                .id(10L)
                .adminId(2L)
                .status(GameStatus.STARTED)
                .entryFee(BigDecimal.TEN)
                .maxPlayers(10)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        GameCard claimantCard = GameCard.builder()
                .id(20L)
                .gameId(10L)
                .playerId(5L)
                .cardId(30L)
                .winner(false)
                .build();

        GameCard otherCard = GameCard.builder()
                .id(21L)
                .gameId(10L)
                .playerId(6L)
                .cardId(31L)
                .winner(false)
                .build();

        Card card = Card.builder()
                .id(30L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        Winner winner = Winner.builder()
                .id(40L)
                .gameId(10L)
                .playerId(5L)
                .cardId(30L)
                .rewardAmount(BigDecimal.ZERO)
                .build();

        when(gameRepository.findById(10L)).thenReturn(java.util.Optional.of(startedGame));
        when(winnerRepository.findByGameId(10L)).thenReturn(List.of());
        when(gameCardRepository.findByGameIdAndPlayerId(10L, 5L)).thenReturn(List.of(claimantCard));
        when(cardRepository.findById(30L)).thenReturn(java.util.Optional.of(card));
        when(calledNumberRepository.findByGameIdOrderByCalledAtAsc(10L)).thenReturn(List.of(
                CalledNumber.builder().id(1L).gameId(10L).number(1).calledAt(LocalDateTime.now()).build(),
                CalledNumber.builder().id(2L).gameId(10L).number(17).calledAt(LocalDateTime.now()).build(),
                CalledNumber.builder().id(3L).gameId(10L).number(49).calledAt(LocalDateTime.now()).build(),
                CalledNumber.builder().id(4L).gameId(10L).number(65).calledAt(LocalDateTime.now()).build()
        ));
        when(gameCardRepository.findByGameId(10L)).thenReturn(List.of(claimantCard, otherCard));
        when(winnerService.createWinner(10L, claimantCard)).thenReturn(winner);
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Winner actual = gameEngineService.claimBingo(10L, 5L);

        assertEquals(winner, actual);
        verify(gameCardRepository).save(claimantCard);
        verify(winnerService).distributeRewards(10L, BigDecimal.valueOf(20), List.of(winner));
        verify(gameRepository, times(2)).save(any(Game.class));
    }

    @Test
    void isWinningCardTreatsCenterAsAutomatic() {
        Card card = Card.builder()
                .id(50L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        boolean actual = gameEngineService.isWinningCard(card, List.of(1, 17, 49, 65));

        assertEquals(true, actual);
    }
}
