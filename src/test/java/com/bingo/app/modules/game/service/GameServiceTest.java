package com.bingo.app.modules.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.bingo.app.modules.game.dto.CreateGameRequest;
import com.bingo.app.modules.game.dto.GameResponse;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.GameCreationException;
import com.bingo.app.exception.GameProgressException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.repository.GameRepository;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gameService, "defaultEntryFee", BigDecimal.ZERO);
        ReflectionTestUtils.setField(gameService, "defaultMaxPlayers", 50);
    }

    @Test
    void createGameWithEntryFeeRejectsWhenAdminAlreadyHasWaitingGame() {
        Game waitingGame = Game.builder()
                .id(10L)
                .adminId(5L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.REGISTRATION_OPEN)).thenReturn(List.of(waitingGame));
        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.IN_PROGRESS)).thenReturn(List.of());

        GameCreationException ex = assertThrows(
                GameCreationException.class,
                () -> gameService.createGameWithEntryFee(5L, new CreateGameRequest(BigDecimal.ZERO, 50))
        );

        assertEquals("You already have an active game. Finish it before creating another one.", ex.getUserMessage());
    }

    @Test
    void createGameWithEntryFeeCreatesWaitingGame() {
        Game savedGame = Game.builder()
                .id(11L)
                .adminId(5L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(BigDecimal.valueOf(20))
                .maxPlayers(30)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.REGISTRATION_OPEN)).thenReturn(List.of());
        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.IN_PROGRESS)).thenReturn(List.of());
        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);

        GameResponse actual = gameService.createGameWithEntryFee(5L, new CreateGameRequest(BigDecimal.valueOf(20), 30));

        assertEquals(11L, actual.id());
        assertEquals(GameStatus.REGISTRATION_OPEN, actual.status());
        assertEquals(BigDecimal.valueOf(20), actual.entryFee());
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    void findCurrentWaitingGameReturnsOldestWaitingGame() {
        Game waitingGame = Game.builder()
                .id(12L)
                .adminId(7L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findFirstByStatusOrderByCreatedAtAsc(GameStatus.REGISTRATION_OPEN))
                .thenReturn(Optional.of(waitingGame));

        Optional<GameResponse> actual = gameService.findCurrentWaitingGame();

        assertTrue(actual.isPresent());
        assertEquals(12L, actual.get().id());
        assertEquals(GameStatus.REGISTRATION_OPEN, actual.get().status());
    }

    @Test
    void startGameForAdminPromotesWaitingGameToStarted() {
        Game waitingGame = Game.builder()
                .id(13L)
                .adminId(5L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findById(13L)).thenReturn(Optional.of(waitingGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameResponse actual = gameService.startGameForAdmin(5L, 13L);

        assertEquals(GameStatus.IN_PROGRESS, actual.status());
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    void startGameForAdminRejectsNonOwnedGame() {
        Game otherAdminsGame = Game.builder()
                .id(13L)
                .adminId(99L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findById(13L)).thenReturn(Optional.of(otherAdminsGame));

        GameProgressException ex = assertThrows(
                GameProgressException.class,
                () -> gameService.startGameForAdmin(5L, 13L)
        );

        assertEquals("You can only manage games created under your account.", ex.getUserMessage());
    }

    @Test
    void findCurrentGamePrefersStartedGameOverWaitingGame() {
        Game startedGame = Game.builder()
                .id(14L)
                .adminId(5L)
                .status(GameStatus.IN_PROGRESS)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findFirstByStatusOrderByStartTimeAsc(GameStatus.IN_PROGRESS))
                .thenReturn(Optional.of(startedGame));

        Optional<GameResponse> actual = gameService.findCurrentGame();

        assertTrue(actual.isPresent());
        assertEquals(14L, actual.get().id());
        assertEquals(GameStatus.IN_PROGRESS, actual.get().status());
    }

    @Test
    void findCurrentGameForAdminFallsBackToWaitingGame() {
        Game waitingGame = Game.builder()
                .id(15L)
                .adminId(5L)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.REGISTRATION_OPEN))
                .thenReturn(Optional.of(waitingGame));

        Optional<GameResponse> actual = gameService.findCurrentGameForAdmin(5L);

        assertTrue(actual.isPresent());
        assertEquals(15L, actual.get().id());
        assertEquals(GameStatus.REGISTRATION_OPEN, actual.get().status());
    }
}
