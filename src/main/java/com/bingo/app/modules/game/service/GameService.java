package com.bingo.app.modules.game.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.bingo.app.modules.game.dto.CreateGameRequest;
import com.bingo.app.modules.game.dto.GameResponse;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.exception.GameCreationException;
import com.bingo.app.exception.GameProgressException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.repository.GameRepository;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.game.default-entry-fee:10}")
    private BigDecimal defaultEntryFee;

    @Value("${app.game.default-max-players:50}")
    private int defaultMaxPlayers;

    private Game createGame(Long adminId, BigDecimal entryFee, Integer maxPlayers) {
        Game game = Game.builder()
                .adminId(adminId)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(entryFee)
                .maxPlayers(maxPlayers != null ? maxPlayers : defaultMaxPlayers)
                .createdAt(LocalDateTime.now())
                .build();

        return gameRepository.save(game);
    }

    @Transactional("tenantTransactionManager")
    public GameResponse createGameWithEntryFee(Long adminId, CreateGameRequest request) {
        if (request.entryFee() == null || request.entryFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new GameCreationException(
                    "Invalid entry fee",
                    "Entry fee must be zero or greater."
            );
        }

        boolean hasWaitingGame = !gameRepository.findByAdminIdAndStatus(adminId, GameStatus.REGISTRATION_OPEN).isEmpty();
        boolean hasStartedGame = !gameRepository.findByAdminIdAndStatus(adminId, GameStatus.IN_PROGRESS).isEmpty();

        if (hasWaitingGame || hasStartedGame) {
            throw new GameCreationException(
                    "Admin already has an active game",
                    "You already have an active game. Finish it before creating another one."
            );
        }

        return GameResponse.from(createGame(adminId, request.entryFee(), request.maxPlayers()));
    }

    public List<Game> findAllGames() {
        return gameRepository.findAll();
    }

    public Optional<GameResponse> findCurrentWaitingGame() {
        return gameRepository.findFirstByStatusOrderByCreatedAtAsc(GameStatus.REGISTRATION_OPEN)
                .map(GameResponse::from);
    }

    public Optional<GameResponse> findCurrentStartedGame() {
        return gameRepository.findFirstByStatusOrderByStartTimeAsc(GameStatus.IN_PROGRESS)
                .map(GameResponse::from);
    }

    public Optional<GameResponse> findCurrentGame() {
        Optional<GameResponse> startedGame = findCurrentStartedGame();
        return startedGame.isPresent() ? startedGame : findCurrentWaitingGame();
    }

    public Optional<GameResponse> findAdminWaitingGame(Long adminId) {
        return gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(adminId, GameStatus.REGISTRATION_OPEN)
                .map(GameResponse::from);
    }

    public Optional<GameResponse> findAdminStartedGame(Long adminId) {
        return gameRepository.findFirstByAdminIdAndStatusOrderByCreatedAtAsc(adminId, GameStatus.IN_PROGRESS)
                .map(GameResponse::from);
    }

    public Optional<GameResponse> findCurrentGameForAdmin(Long adminId) {
        Optional<GameResponse> startedGame = findAdminStartedGame(adminId);
        return startedGame.isPresent() ? startedGame : findAdminWaitingGame(adminId);
    }

    public Game requireAdminGame(Long adminId, Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameProgressException("Game not found", "The game could not be found."));

        if (!adminId.equals(game.getAdminId())) {
            throw new GameProgressException(
                    "Game ownership mismatch",
                    "You can only manage games created under your account."
            );
        }

        return game;
    }

    @Transactional("tenantTransactionManager")
    public GameResponse startGameForAdmin(Long adminId, Long gameId) {
        Game game = requireAdminGame(adminId, gameId);
        if (game.getStatus() == GameStatus.IN_PROGRESS) {
            throw new GameProgressException(
                    "Game already started",
                    "This game is already started."
            );
        }

        game.setStatus(GameStatus.IN_PROGRESS);
        game.setStartTime(LocalDateTime.now());
        Game saved = gameRepository.save(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.IN_PROGRESS);
        return GameResponse.from(saved);
    }

    @Transactional("tenantTransactionManager")
    public GameResponse endGameForAdmin(Long adminId, Long gameId) {
        Game game = requireAdminGame(adminId, gameId);
        if (game.getStatus() == GameStatus.ENDED) {
            throw new GameProgressException(
                    "Game already ended",
                    "This game is already ended."
            );
        }

        game.setStatus(GameStatus.ENDED);
        Game saved = gameRepository.save(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.ENDED);
        return GameResponse.from(saved);
    }

    @Transactional("tenantTransactionManager")
    public void startGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow();

        game.setStatus(GameStatus.IN_PROGRESS);
        game.setStartTime(LocalDateTime.now());

        gameRepository.save(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.IN_PROGRESS);
    }

    @Transactional("tenantTransactionManager")
    public void endGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow();

        game.setStatus(GameStatus.ENDED);
        gameRepository.save(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/status", GameStatus.ENDED);
    }
}
