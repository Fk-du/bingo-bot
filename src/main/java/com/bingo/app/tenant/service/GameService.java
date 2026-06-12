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

    @Transactional
    public Game createGameWithEntryFee(Long adminUserId, CreateGameRequest request) {
        if (gameRepository.hasActiveGame(adminUserId)) {
            throw new RuntimeException("Admin already has an active game");
        }

        Game game = Game.builder()
                .adminUserId(adminUserId)
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
        log.info("Game created: id={}, adminUserId={}, entryFee={}", saved.getId(), adminUserId, request.getEntryFee());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Game> findAdminWaitingGame(Long adminUserId) {
        return gameRepository.findByAdminUserIdAndStatus(adminUserId, GameStatus.REGISTRATION_OPEN);
    }

    @Transactional(readOnly = true)
    public Optional<Game> findAdminStartedGame(Long adminUserId) {
        return gameRepository.findByAdminUserIdAndStatus(adminUserId, GameStatus.IN_PROGRESS);
    }

    @Transactional(readOnly = true)
    public Optional<Game> findCurrentGameForAdmin(Long adminUserId) {
        return gameRepository.findByAdminUserIdAndStatusIn(adminUserId,
                List.of(GameStatus.REGISTRATION_OPEN, GameStatus.IN_PROGRESS, GameStatus.CLAIM_PENDING));
    }

    @Transactional
    public Game startGameForAdmin(Long adminUserId, Long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Game does not belong to this admin");
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new RuntimeException("Game cannot be started. Current status: " + game.getStatus());
        }

        List<Integer> sequence = generateSealedNumberSequence();
        saveNumberSequence(gameId, sequence);

        game.setStatus(GameStatus.IN_PROGRESS);
        game.setStartTime(LocalDateTime.now());
        game.setCurrentCallIndex(0);
        game.setTotalNumbersCalled(0);

        Game saved = gameRepository.save(game);
        log.info("Game started: id={}, adminUserId={}", gameId, adminUserId);
        return saved;
    }

    @Transactional
    public void cancelGame(Long gameId, Long adminUserId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Game does not belong to this admin");
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new RuntimeException("Can only cancel a game that hasn't started yet");
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

        log.info("Game cancelled: id={}, adminUserId={}", gameId, adminUserId);
    }

    @Transactional
    public Game endGameManually(Long gameId, Long adminUserId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Game does not belong to this admin");
        }

        if (game.getStatus() == GameStatus.ENDED) {
            throw new RuntimeException("Game already ended");
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());

        Game saved = gameRepository.save(game);
        log.info("Game manually ended: id={}, adminUserId={}", gameId, adminUserId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Game> getGameById(Long gameId) {
        return gameRepository.findById(gameId);
    }

    @Transactional(readOnly = true)
    public List<Game> getAllGamesForAdmin(Long adminUserId) {
        return gameRepository.findAllByAdminUserIdOrderByCreatedAtDesc(adminUserId);
    }

    @Transactional(readOnly = true)
    public List<Game> findAllGames() {
        return gameRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public long getActiveGamesCount(Long adminUserId) {
        return gameRepository.countByAdminUserIdAndStatusIn(adminUserId,
                List.of(GameStatus.REGISTRATION_OPEN, GameStatus.IN_PROGRESS, GameStatus.CLAIM_PENDING));
    }

    @Transactional
    public Game updateGameSettings(Long gameId, Long adminUserId, Integer maxPlayers, Integer callInterval, String winningPattern) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Game does not belong to this admin");
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
