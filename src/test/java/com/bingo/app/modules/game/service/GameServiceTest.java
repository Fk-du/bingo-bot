package com.bingo.app.modules.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
import com.bingo.app.modules.game.service.GameService;

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
    void createDefaultGameRejectsWhenAdminAlreadyHasWaitingGame() {
        Game waitingGame = Game.builder()
                .id(10L)
                .adminId(5L)
                .status(GameStatus.WAITING)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.WAITING)).thenReturn(List.of(waitingGame));
        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.STARTED)).thenReturn(List.of());

        GameCreationException ex = assertThrows(
                GameCreationException.class,
                () -> gameService.createDefaultGame(5L)
        );

        assertEquals("You already have an active game. Finish it before creating another one.", ex.getUserMessage());
    }

    @Test
    void createDefaultGameCreatesWaitingGameWithDefaults() {
        Game savedGame = Game.builder()
                .id(11L)
                .adminId(5L)
                .status(GameStatus.WAITING)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.WAITING)).thenReturn(List.of());
        when(gameRepository.findByAdminIdAndStatus(5L, GameStatus.STARTED)).thenReturn(List.of());
        when(gameRepository.save(org.mockito.ArgumentMatchers.any(Game.class))).thenReturn(savedGame);

        Game actual = gameService.createDefaultGame(5L);

        assertEquals(savedGame, actual);
        verify(gameRepository).save(org.mockito.ArgumentMatchers.any(Game.class));
    }

    @Test
    void findCurrentWaitingGameReturnsOldestWaitingGame() {
        Game waitingGame = Game.builder()
                .id(12L)
                .adminId(7L)
                .status(GameStatus.WAITING)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findFirstByStatusOrderByCreatedAtAsc(GameStatus.WAITING))
                .thenReturn(java.util.Optional.of(waitingGame));

        java.util.Optional<Game> actual = gameService.findCurrentWaitingGame();

        assertTrue(actual.isPresent());
        assertEquals(waitingGame, actual.get());
    }

    @Test
    void startCurrentGameForAdminPromotesWaitingGameToStarted() {
        Game waitingGame = Game.builder()
                .id(13L)
                .adminId(5L)
                .status(GameStatus.WAITING)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.STARTED))
                .thenReturn(java.util.Optional.empty());
        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.WAITING))
                .thenReturn(java.util.Optional.of(waitingGame));
        when(gameRepository.save(org.mockito.ArgumentMatchers.any(Game.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Game actual = gameService.startCurrentGameForAdmin(5L);

        assertEquals(GameStatus.STARTED, actual.getStatus());
        verify(gameRepository).save(org.mockito.ArgumentMatchers.any(Game.class));
    }

    @Test
    void startCurrentGameForAdminRejectsMissingWaitingGame() {
        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.STARTED))
                .thenReturn(java.util.Optional.empty());
        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.WAITING))
                .thenReturn(java.util.Optional.empty());

        GameProgressException ex = assertThrows(
                GameProgressException.class,
                () -> gameService.startCurrentGameForAdmin(5L)
        );

        assertEquals("You do not have a waiting game to start.", ex.getUserMessage());
    }

    @Test
    void findCurrentGamePrefersStartedGameOverWaitingGame() {
        Game startedGame = Game.builder()
                .id(14L)
                .adminId(5L)
                .status(GameStatus.STARTED)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findFirstByStatusOrderByStartTimeAsc(GameStatus.STARTED))
                .thenReturn(java.util.Optional.of(startedGame));

        java.util.Optional<Game> actual = gameService.findCurrentGame();

        assertTrue(actual.isPresent());
        assertEquals(startedGame, actual.get());
    }

    @Test
    void findCurrentGameForAdminFallsBackToWaitingGame() {
        Game waitingGame = Game.builder()
                .id(15L)
                .adminId(5L)
                .status(GameStatus.WAITING)
                .entryFee(BigDecimal.ZERO)
                .maxPlayers(50)
                .createdAt(LocalDateTime.now())
                .build();

        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.STARTED))
                .thenReturn(java.util.Optional.empty());
        when(gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(5L, GameStatus.WAITING))
                .thenReturn(java.util.Optional.of(waitingGame));

        java.util.Optional<Game> actual = gameService.findCurrentGameForAdmin(5L);

        assertTrue(actual.isPresent());
        assertEquals(waitingGame, actual.get());
    }
}
