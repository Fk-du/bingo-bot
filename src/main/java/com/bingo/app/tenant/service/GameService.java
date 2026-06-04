package com.bingo.app.tenant.service;

import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.tenant.entity.CalledNumber;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.repository.CalledNumberRepository;
import com.bingo.app.tenant.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRepository gameRepository;
    private final CalledNumberRepository calledNumberRepository;

    /**
     * Create a new game with entry fee
     */
    @Transactional
    public Game createGameWithEntryFee(Long agentId, CreateGameRequest request) {
        if (gameRepository.hasActiveGame(agentId)) {
            throw new RuntimeException("Agent already has an active game");
        }

        Game game = Game.builder()
                .agentId(agentId)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(request.getEntryFee())
                .maxPlayers(request.getMaxPlayers() != null ? request.getMaxPlayers() : 50)
                .currentCallIndex(0)
                .totalNumbersCalled(0)
                .prizePool(BigDecimal.ZERO)
                .winningPattern(request.getWinningPattern() != null ? request.getWinningPattern() : "SINGLE_LINE")
                .callInterval(5)
                .createdAt(LocalDateTime.now())
                .build();

        Game saved = gameRepository.save(game);
        log.info("Game created: id={}, agentId={}, entryFee={}", saved.getId(), agentId, request.getEntryFee());
        return saved;
    }

    /**
     * Find waiting game for admin (game in REGISTRATION_OPEN status)
     */
    @Transactional(readOnly = true)
    public Optional<Game> findAdminWaitingGame(Long agentId) {
        return gameRepository.findByAgentIdAndStatus(agentId, GameStatus.REGISTRATION_OPEN);
    }

    /**
     * Find started game for admin (game in IN_PROGRESS status)
     */
    @Transactional(readOnly = true)
    public Optional<Game> findAdminStartedGame(Long agentId) {
        return gameRepository.findByAgentIdAndStatus(agentId, GameStatus.IN_PROGRESS);
    }

    /**
     * Find current game for admin (any active game)
     */
    @Transactional(readOnly = true)
    public Optional<Game> findCurrentGameForAdmin(Long agentId) {
        return gameRepository.findByAgentIdAndStatusIn(agentId,
                List.of(GameStatus.REGISTRATION_OPEN, GameStatus.IN_PROGRESS, GameStatus.CLAIM_PENDING));
    }

    /**
     * Start game for admin
     */
    @Transactional
    public Game startGameForAdmin(Long agentId, Long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAgentId().equals(agentId)) {
            throw new RuntimeException("Game does not belong to this agent");
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new RuntimeException("Game cannot be started. Current status: " + game.getStatus());
        }

        // Generate sealed number sequence (Fisher-Yates shuffle of 1-75)
        List<Integer> sequence = generateSealedNumberSequence();
        saveNumberSequence(gameId, sequence);

        // Update game status
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setStartTime(LocalDateTime.now());
        game.setCurrentCallIndex(0);
        game.setTotalNumbersCalled(0);

        Game saved = gameRepository.save(game);
        log.info("Game started: id={}, agentId={}", gameId, agentId);
        return saved;
    }

    /**
     * Cancel a game in REGISTRATION_OPEN (not enough players, etc.)
     */
    @Transactional
    public void cancelGame(Long gameId, Long agentId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAgentId().equals(agentId)) {
            throw new RuntimeException("Game does not belong to this agent");
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new RuntimeException("Can only cancel a game that hasn't started yet");
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

        log.info("Game cancelled: id={}, agentId={}", gameId, agentId);
    }

    /**
     * End game manually
     */
    @Transactional
    public Game endGameManually(Long gameId, Long agentId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAgentId().equals(agentId)) {
            throw new RuntimeException("Game does not belong to this agent");
        }

        if (game.getStatus() == GameStatus.ENDED) {
            throw new RuntimeException("Game already ended");
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());

        Game saved = gameRepository.save(game);
        log.info("Game manually ended: id={}, agentId={}", gameId, agentId);
        return saved;
    }

    /**
     * Get game by ID
     */
    @Transactional(readOnly = true)
    public Optional<Game> getGameById(Long gameId) {
        return gameRepository.findById(gameId);
    }

    /**
     * Get all games for an agent
     */
    @Transactional(readOnly = true)
    public List<Game> getAllGamesForAgent(Long agentId) {
        return gameRepository.findAllByAgentIdOrderByCreatedAtDesc(agentId);
    }

    /**
     * Get all games (for super admin)
     */
    @Transactional(readOnly = true)
    public List<Game> findAllGames() {
        return gameRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get active games count for an agent
     */
    @Transactional(readOnly = true)
    public long getActiveGamesCount(Long agentId) {
        return gameRepository.countByAgentIdAndStatusIn(agentId,
                List.of(GameStatus.REGISTRATION_OPEN, GameStatus.IN_PROGRESS, GameStatus.CLAIM_PENDING));
    }

    /**
     * Update game settings
     */
    @Transactional
    public Game updateGameSettings(Long gameId, Long agentId, Integer maxPlayers, Integer callInterval, String winningPattern) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAgentId().equals(agentId)) {
            throw new RuntimeException("Game does not belong to this agent");
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new RuntimeException("Cannot update game that has already started");
        }

        if (maxPlayers != null) {
            game.setMaxPlayers(maxPlayers);
        }
        if (callInterval != null) {
            game.setCallInterval(callInterval);
        }
        if (winningPattern != null) {
            game.setWinningPattern(winningPattern);
        }

        return gameRepository.save(game);
    }

    /**
     * Generate sealed number sequence (Fisher-Yates shuffle)
     */
    private List<Integer> generateSealedNumberSequence() {
        List<Integer> numbers = IntStream.rangeClosed(1, 75)
                .boxed()
                .collect(Collectors.toList());

        SecureRandom random = new SecureRandom();
        for (int i = numbers.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = numbers.get(i);
            numbers.set(i, numbers.get(j));
            numbers.set(j, temp);
        }

        return numbers;
    }

    /**
     * Save number sequence for a game
     */
    private void saveNumberSequence(Long gameId, List<Integer> sequence) {
        for (int i = 0; i < sequence.size(); i++) {
            CalledNumber calledNumber = CalledNumber.builder()
                    .gameId(gameId)
                    .number(sequence.get(i))
                    .sequenceIndex(i)
                    .build();
            calledNumberRepository.save(calledNumber);
        }
        log.info("Saved number sequence for game {}: {} numbers", gameId, sequence.size());
    }
}