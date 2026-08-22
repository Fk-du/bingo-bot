package com.bingo.app.tenant.service;

import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.entity.CalledNumber;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.entity.GameCard;
import com.bingo.app.tenant.entity.Transaction;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.enums.TransactionStatus;
import com.bingo.app.tenant.enums.TransactionType;
import com.bingo.app.tenant.repository.CalledNumberRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.bingo.app.tenant.repository.TransactionRepository;
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
    private final GameCardRepository gameCardRepository;
    private final TransactionRepository transactionRepository;
    private final PlayerService playerService;
    private final TenantMapper tenantMapper;

    @Transactional(transactionManager = "tenantTransactionManager")
    public GameResponse createGameWithEntryFee(Long adminUserId, CreateGameRequest request) {
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
                .callInterval(request.getCallInterval() != null ? request.getCallInterval() : 5)
                .createdAt(LocalDateTime.now())
                .build();

        Game saved = gameRepository.save(game);
        log.info("Game created: id={}, adminUserId={}, entryFee={}", saved.getId(), adminUserId, request.getEntryFee());
        return tenantMapper.toDto(saved);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<GameResponse> findAdminWaitingGame(Long adminUserId) {
        return gameRepository.findByAdminUserIdAndStatus(adminUserId, GameStatus.REGISTRATION_OPEN)
                .map(tenantMapper::toDto);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<GameResponse> findAdminStartedGame(Long adminUserId) {
        return gameRepository.findByAdminUserIdAndStatus(adminUserId, GameStatus.IN_PROGRESS)
                .map(tenantMapper::toDto);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<GameResponse> findCurrentGameForAdmin(Long adminUserId) {
        return gameRepository.findByAdminUserIdAndStatusIn(adminUserId,
                List.of(GameStatus.REGISTRATION_OPEN, GameStatus.IN_PROGRESS, GameStatus.PAUSED, GameStatus.CLAIM_PENDING))
                .map(tenantMapper::toDto);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public GameResponse startGameForAdmin(Long adminUserId, Long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Game does not belong to this admin");
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new RuntimeException("Game cannot be started. Current status: " + game.getStatus());
        }

        long playerCount = gameCardRepository.countByGameId(gameId);
        if (playerCount < 2) {
            throw new RuntimeException("Game needs at least 2 players to start. Currently: " + playerCount);
        }

        List<Integer> sequence = generateSealedNumberSequence();
        saveNumberSequence(gameId, sequence);

        game.setStatus(GameStatus.IN_PROGRESS);
        game.setStartTime(LocalDateTime.now());
        game.setCurrentCallIndex(0);
        game.setTotalNumbersCalled(0);

        Game saved = gameRepository.save(game);
        log.info("Game started: id={}, adminUserId={}", gameId, adminUserId);
        return tenantMapper.toDto(saved);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void cancelGame(Long gameId, Long adminUserId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Game does not belong to this admin");
        }

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new RuntimeException("Can only cancel a game that hasn't started yet");
        }

        refundPlayersForGame(gameId, game.getEntryFee());

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

        log.info("Game cancelled: id={}, adminUserId={}", gameId, adminUserId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public GameResponse endGameManually(Long gameId, Long adminUserId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!game.getAdminUserId().equals(adminUserId)) {
            throw new RuntimeException("Game does not belong to this admin");
        }

        if (game.getStatus() == GameStatus.ENDED) {
            throw new RuntimeException("Game already ended");
        }

        refundNonWinnersForGame(gameId, game.getEntryFee());

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());

        Game saved = gameRepository.save(game);
        log.info("Game manually ended: id={}, adminUserId={}", gameId, adminUserId);
        return tenantMapper.toDto(saved);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<GameResponse> getGameById(Long gameId) {
        return gameRepository.findById(gameId).map(tenantMapper::toDto);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<GameResponse> getAllGamesForAdmin(Long adminUserId) {
        return gameRepository.findAllByAdminUserIdOrderByCreatedAtDesc(adminUserId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<GameResponse> findAllGames() {
        return gameRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<GameResponse> getGamesForPlayer(Long playerId) {
        List<Long> gameIds = gameCardRepository.findByPlayerIdOrderByCreatedAtDesc(playerId).stream()
                .map(GameCard::getGameId)
                .distinct()
                .toList();
        return gameIds.stream()
                .map(gameRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public long getActiveGamesCount(Long adminUserId) {
        return gameRepository.countByAdminUserIdAndStatusIn(adminUserId,
                List.of(GameStatus.REGISTRATION_OPEN, GameStatus.IN_PROGRESS, GameStatus.PAUSED, GameStatus.CLAIM_PENDING));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public GameResponse updateGameSettings(Long gameId, Long adminUserId, Integer maxPlayers, Integer callInterval, String winningPattern) {
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

        return tenantMapper.toDto(gameRepository.save(game));
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

    /**
     * Refund entry fees to all registered players (for cancelled games).
     */
    private void refundPlayersForGame(Long gameId, BigDecimal entryFee) {
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<GameCard> allCards = gameCardRepository.findByGameId(gameId);
        for (GameCard card : allCards) {
            Long playerId = card.getPlayerId();
            playerService.addBalance(playerId, entryFee);

            transactionRepository.save(Transaction.builder()
                    .userId(playerId)
                    .type(TransactionType.REFUND.name())
                    .amount(entryFee)
                    .status(TransactionStatus.COMPLETED)
                    .referenceId(gameId)
                    .description("Entry fee refund for cancelled game " + gameId)
                    .createdAt(LocalDateTime.now())
                    .build());

            log.info("Refunded {} to player {} for cancelled game {}", entryFee, playerId, gameId);
        }
    }

    /**
     * Refund entry fees to non-winner registered players (for manually ended or no-winner games).
     */
    private void refundNonWinnersForGame(Long gameId, BigDecimal entryFee) {
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<GameCard> allCards = gameCardRepository.findByGameId(gameId);
        for (GameCard card : allCards) {
            if (card.isWinner()) {
                continue;
            }
            Long playerId = card.getPlayerId();
            playerService.addBalance(playerId, entryFee);

            transactionRepository.save(Transaction.builder()
                    .userId(playerId)
                    .type(TransactionType.REFUND.name())
                    .amount(entryFee)
                    .status(TransactionStatus.COMPLETED)
                    .referenceId(gameId)
                    .description("Entry fee refund for ended game " + gameId)
                    .createdAt(LocalDateTime.now())
                    .build());

            log.info("Refunded {} to player {} for ended game {}", entryFee, playerId, gameId);
        }
    }
}
