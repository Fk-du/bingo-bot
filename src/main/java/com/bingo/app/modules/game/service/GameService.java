package com.bingo.app.modules.game.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.GameCreationException;
import com.bingo.app.exception.GameProgressException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.repository.GameRepository;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.game.default-entry-fee:0}")
    private BigDecimal defaultEntryFee;

    @Value("${app.game.default-max-players:50}")
    private int defaultMaxPlayers;

    public Game createGame(Long adminId, BigDecimal entryFee, int maxPlayers) {
        Game game = Game.builder()
                .adminId(adminId)
                .status(GameStatus.WAITING)
                .entryFee(entryFee)
                .maxPlayers(maxPlayers)
                .createdAt(LocalDateTime.now())
                .build();

        return gameRepository.save(game);
    }

    public Game createDefaultGame(Long adminId) {
        return createGameWithEntryFee(adminId, defaultEntryFee);
    }

    public Game createGameWithEntryFee(Long adminId, BigDecimal entryFee) {
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new GameCreationException(
                    "Invalid entry fee",
                    "Entry fee must be zero or greater."
            );
        }

        boolean hasWaitingGame = !gameRepository.findByAdminIdAndStatus(adminId, GameStatus.WAITING).isEmpty();
        boolean hasStartedGame = !gameRepository.findByAdminIdAndStatus(adminId, GameStatus.STARTED).isEmpty();

        if (hasWaitingGame || hasStartedGame) {
            throw new GameCreationException(
                    "Admin already has an active game",
                    "You already have an active game. Finish it before creating another one."
            );
        }

        return createGame(adminId, entryFee, defaultMaxPlayers);
    }

    public Optional<Game> findCurrentWaitingGame() {
        return gameRepository.findFirstByStatusOrderByCreatedAtAsc(GameStatus.WAITING);
    }

    public Optional<Game> findCurrentStartedGame() {
        return gameRepository.findFirstByStatusOrderByStartTimeAsc(GameStatus.STARTED);
    }

    public Optional<Game> findCurrentGame() {
        Optional<Game> startedGame = findCurrentStartedGame();
        return startedGame.isPresent() ? startedGame : findCurrentWaitingGame();
    }

    public Optional<Game> findAdminWaitingGame(Long adminId) {
        return gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(adminId, GameStatus.WAITING);
    }

    public Optional<Game> findAdminStartedGame(Long adminId) {
        return gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(adminId, GameStatus.STARTED);
    }

    public Optional<Game> findCurrentGameForAdmin(Long adminId) {
        Optional<Game> startedGame = findAdminStartedGame(adminId);
        return startedGame.isPresent() ? startedGame : findAdminWaitingGame(adminId);
    }

    public Game startCurrentGameForAdmin(Long adminId) {
        Game startedGame = findAdminStartedGame(adminId).orElse(null);
        if (startedGame != null) {
            throw new GameProgressException(
                    "Admin already has a started game",
                    "You already have a started game. Call the next number or end that game first."
            );
        }

        Game waitingGame = findAdminWaitingGame(adminId).orElseThrow(() -> new GameProgressException(
                "No waiting game found for admin",
                "You do not have a waiting game to start."
        ));

        waitingGame.setStatus(GameStatus.STARTED);
        waitingGame.setStartTime(LocalDateTime.now());

        Game saved = gameRepository.save(waitingGame);
        messagingTemplate.convertAndSend("/topic/game/" + saved.getId() + "/status", GameStatus.STARTED);
        return saved;
    }

    public void startGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow();

        game.setStatus(GameStatus.STARTED);
        game.setStartTime(LocalDateTime.now());

        gameRepository.save(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.STARTED);
    }

    public void endGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow();

        game.setStatus(GameStatus.ENDED);
        gameRepository.save(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.ENDED);
    }
}
