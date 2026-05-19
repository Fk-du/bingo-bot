package com.bingo.app.modules.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.bingo.app.modules.game.dto.WinnerResponse;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.GameProgressException;
import com.bingo.app.exception.PlayerActionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private BingoClaimRepository bingoClaimRepository;

    @InjectMocks
    private GameEngineService gameEngineService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gameEngineService, "numberRange", 75);
        ReflectionTestUtils.setField(gameEngineService, "cardSize", 25);
    }

    @Test
    void callNumberAdvancesThroughSealedSequence() {
        Game game = Game.builder()
                .id(10L)
                .adminId(2L)
                .status(GameStatus.IN_PROGRESS)
                .entryFee(BigDecimal.TEN)
                .maxPlayers(10)
                .currentCallIndex(74)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        CalledNumber nextNumber = CalledNumber.builder()
                .id(100L)
                .gameId(10L)
                .number(75)
                .sequenceIndex(74)
                .calledAt(null)
                .build();

        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(calledNumberRepository.findByGameIdAndSequenceIndex(10L, 74))
                .thenReturn(Optional.of(nextNumber));

        Integer actual = gameEngineService.callNumber(10L);

        assertEquals(75, actual);
        verify(calledNumberRepository).save(any(CalledNumber.class));
    }

    @Test
    void callNumberAutoEndsWhenAllNumbersAreExhausted() {
        Game game = Game.builder()
                .id(10L)
                .adminId(2L)
                .status(GameStatus.IN_PROGRESS)
                .entryFee(BigDecimal.TEN)
                .maxPlayers(10)
                .currentCallIndex(75)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));

        Integer result = gameEngineService.callNumber(10L);

        assertNull(result);
        assertEquals(GameStatus.ENDED, game.getStatus());
        verify(gameRepository).save(game);
        verify(messagingTemplate).convertAndSend("/topic/game/10/status", GameStatus.ENDED);
    }

    @Test
    void claimBingoRejectsNonWinningCard() {
        Game startedGame = Game.builder()
                .id(10L)
                .adminId(2L)
                .status(GameStatus.IN_PROGRESS)
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

        when(gameRepository.findById(10L)).thenReturn(Optional.of(startedGame));
        when(winnerRepository.findByGameId(10L)).thenReturn(List.of());
        when(gameCardRepository.findByGameIdAndPlayerId(10L, 5L)).thenReturn(List.of(gameCard));
        when(cardRepository.findById(30L)).thenReturn(Optional.of(card));
        when(calledNumberRepository.findByGameIdAndCalledAtIsNotNullOrderBySequenceIndexAsc(10L))
                .thenReturn(List.of(
                        CalledNumber.builder().id(1L).gameId(10L).number(1).sequenceIndex(0).calledAt(LocalDateTime.now()).build(),
                        CalledNumber.builder().id(2L).gameId(10L).number(7).sequenceIndex(1).calledAt(LocalDateTime.now()).build(),
                        CalledNumber.builder().id(3L).gameId(10L).number(14).sequenceIndex(2).calledAt(LocalDateTime.now()).build()
                ));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
                .status(GameStatus.IN_PROGRESS)
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
                .rewardAmount(BigDecimal.valueOf(9))
                .build();

        when(gameRepository.findById(10L)).thenReturn(Optional.of(startedGame));
        when(winnerRepository.findByGameId(10L)).thenReturn(List.of());
        when(gameCardRepository.findByGameIdAndPlayerId(10L, 5L)).thenReturn(List.of(claimantCard));
        when(cardRepository.findById(30L)).thenReturn(Optional.of(card));
        when(calledNumberRepository.findByGameIdAndCalledAtIsNotNullOrderBySequenceIndexAsc(10L))
                .thenReturn(List.of(
                        CalledNumber.builder().id(1L).gameId(10L).number(1).sequenceIndex(0).calledAt(LocalDateTime.now()).build(),
                        CalledNumber.builder().id(2L).gameId(10L).number(17).sequenceIndex(1).calledAt(LocalDateTime.now()).build(),
                        CalledNumber.builder().id(3L).gameId(10L).number(49).sequenceIndex(2).calledAt(LocalDateTime.now()).build(),
                        CalledNumber.builder().id(4L).gameId(10L).number(65).sequenceIndex(3).calledAt(LocalDateTime.now()).build()
                ));
        when(gameCardRepository.findByGameId(10L)).thenReturn(List.of(claimantCard, otherCard));
        when(winnerService.createWinner(eq(10L), any(GameCard.class))).thenReturn(winner);
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WinnerResponse actual = gameEngineService.claimBingo(10L, 5L);

        assertEquals(5L, actual.playerId());
        assertEquals(BigDecimal.valueOf(9), actual.rewardAmount());
        verify(gameCardRepository).save(claimantCard);
        verify(winnerService).distributeRewards(eq(10L), eq(BigDecimal.valueOf(20)), any());
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

        assertTrue(actual);
    }

    @Test
    void isWinningCardDetectsFourCorners() {
        Card card = Card.builder()
                .id(50L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        boolean actual = gameEngineService.isWinningCard(card, List.of(1, 61, 5, 65));

        assertTrue(actual, "Four corners pattern should match");
    }

    @Test
    void isWinningCardDetectsXPattern() {
        Card card = Card.builder()
                .id(50L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        boolean actual = gameEngineService.isWinningCard(card,
                List.of(1, 17, 49, 65, 61, 47, 19, 5));

        assertTrue(actual, "X pattern (both diagonals) should match");
    }

    @Test
    void isWinningCardDetectsPostageStamp() {
        Card card = Card.builder()
                .id(50L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        boolean actual = gameEngineService.isWinningCard(card, List.of(1, 16, 2, 17));

        assertTrue(actual, "Postage stamp (top-left 2x2) should match");
    }

    @Test
    void isWinningCardDetectsPictureFrame() {
        Card card = Card.builder()
                .id(50L)
                .numbers("1,16,31,46,61,2,17,32,47,62,3,18,FREE,48,63,4,19,34,49,64,5,20,35,50,65")
                .used(true)
                .build();

        boolean actual = gameEngineService.isWinningCard(card,
                List.of(1, 16, 31, 46, 61, 2, 3, 4, 5, 20, 35, 50, 65, 62, 63, 64));

        assertTrue(actual, "Picture frame (outer border) should match");
    }

    @Test
    void isWinningCardDetectsBlackout() {
        Card card = Card.builder()
                .id(50L)
                .numbers("10,11,12,13,14,15,16,17,18,19,20,21,FREE,23,24,25,26,27,28,29,30,31,32,33,34")
                .used(true)
                .build();

        boolean actual = gameEngineService.isWinningCard(card,
                List.of(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                        23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34));

        assertTrue(actual, "Blackout (full card) should match");
    }
}
