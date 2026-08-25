package com.bingo.app.tenant.dto.mapper;

import com.bingo.app.master.entity.User;
import com.bingo.app.tenant.dto.response.*;
import com.bingo.app.tenant.entity.*;
import com.bingo.app.tenant.service.GameEngineService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TenantMapper {

    public GameResponse toDto(Game game) {
        if (game == null) return null;
        return GameResponse.builder()
                .id(game.getId())
                .adminUserId(game.getAdminUserId())
                .status(game.getStatus())
                .entryFee(game.getEntryFee())
                .maxPlayers(game.getMaxPlayers())
                .currentCallIndex(game.getCurrentCallIndex())
                .totalNumbersCalled(game.getTotalNumbersCalled())
                .prizePool(game.getPrizePool())
                .winningPattern(game.getWinningPattern())
                .callInterval(game.getCallInterval())
                .commissionPercent(game.getCommissionPercent())
                .autoMark(Boolean.TRUE.equals(game.getAutoMark()))
                .startTime(game.getStartTime())
                .endTime(game.getEndTime())
                .createdAt(game.getCreatedAt())
                .build();
    }

    public PlayerResponse toDto(Player player) {
        if (player == null) return null;
        return PlayerResponse.builder()
                .id(player.getId())
                .userId(player.getUserId())
                .adminUserId(player.getAdminUserId())
                .parentId(player.getParentId())
                .balance(player.getBalance())
                .frozenBalance(player.getFrozenBalance())
                .createdAt(player.getCreatedAt())
                .build();
    }

    public CardResponse toDto(Card card) {
        if (card == null) return null;
        return CardResponse.builder()
                .id(card.getId())
                .numbers(card.getNumbers())
                .numbersHash(card.getNumbersHash())
                .used(card.isUsed())
                .usageCount(card.getUsageCount())
                .winRate(card.getWinRate())
                .createdAt(card.getCreatedAt())
                .build();
    }

    public PlayerCardResponse toDto(PlayerCard playerCard) {
        if (playerCard == null) return null;
        return PlayerCardResponse.builder()
                .id(playerCard.getId())
                .playerId(playerCard.getPlayerId())
                .card(toDto(playerCard.getCard()))
                .status(playerCard.getStatus())
                .gamesPlayed(playerCard.getGamesPlayed())
                .gamesWon(playerCard.getGamesWon())
                .assignedAt(playerCard.getAssignedAt())
                .unassignedAt(playerCard.getUnassignedAt())
                .build();
    }

    public GameCardResponse toDto(GameCard gameCard) {
        if (gameCard == null) return null;
        return GameCardResponse.builder()
                .id(gameCard.getId())
                .gameId(gameCard.getGameId())
                .playerId(gameCard.getPlayerId())
                .card(toDto(gameCard.getCard()))
                .winner(gameCard.isWinner())
                .createdAt(gameCard.getCreatedAt())
                .build();
    }

    public CalledNumberResponse toDto(CalledNumber calledNumber) {
        if (calledNumber == null) return null;
        return CalledNumberResponse.builder()
                .id(calledNumber.getId())
                .gameId(calledNumber.getGameId())
                .number(calledNumber.getNumber())
                .sequenceIndex(calledNumber.getSequenceIndex())
                .calledAt(calledNumber.getCalledAt())
                .build();
    }

    public BingoClaimResponse toDto(BingoClaim claim) {
        if (claim == null) return null;
        return BingoClaimResponse.builder()
                .id(claim.getId())
                .gameId(claim.getGameId())
                .playerId(claim.getPlayerId())
                .cardId(claim.getCardId())
                .cardSnapshot(claim.getCardSnapshot())
                .calledNumbersSnapshot(claim.getCalledNumbersSnapshot())
                .result(claim.getResult())
                .rewardAmount(claim.getRewardAmount())
                .validatedBy(claim.getValidatedBy())
                .rejectionReason(claim.getRejectionReason())
                .claimedAt(claim.getClaimedAt())
                .validatedAt(claim.getValidatedAt())
                .build();
    }

    public TransactionResponse toDto(Transaction transaction) {
        if (transaction == null) return null;
        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .referenceId(transaction.getReferenceId())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    public CoinRequestResponse toDto(CoinRequest coinRequest) {
        if (coinRequest == null) return null;
        return CoinRequestResponse.builder()
                .id(coinRequest.getId())
                .userId(coinRequest.getUserId())
                .amount(coinRequest.getAmount())
                .screenshotUrl(coinRequest.getScreenshotUrl())
                .status(coinRequest.getStatus())
                .approvedBy(coinRequest.getApprovedBy())
                .approvedAt(coinRequest.getApprovedAt())
                .rejectionReason(coinRequest.getRejectionReason())
                .createdAt(coinRequest.getCreatedAt())
                .build();
    }

    public WithdrawalResponse toDto(Withdrawal withdrawal) {
        if (withdrawal == null) return null;
        return WithdrawalResponse.builder()
                .id(withdrawal.getId())
                .userId(withdrawal.getUserId())
                .amount(withdrawal.getAmount())
                .payoutMethod(withdrawal.getPayoutMethod())
                .payoutDetails(withdrawal.getPayoutDetails())
                .status(withdrawal.getStatus())
                .processedBy(withdrawal.getProcessedBy())
                .processedAt(withdrawal.getProcessedAt())
                .rejectionReason(withdrawal.getRejectionReason())
                .createdAt(withdrawal.getCreatedAt())
                .build();
    }

    public AuditLogResponse toDto(AuditLog auditLog) {
        if (auditLog == null) return null;
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUserId())
                .action(auditLog.getAction())
                .details(auditLog.getDetails())
                .ipAddress(auditLog.getIpAddress())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    public GameStateResponse toGameStateDto(GameEngineService.GameState state) {
        if (state == null) return null;
        List<String> labeled = state.getCalledNumbers() != null
                ? state.getCalledNumbers().stream().map(GameStateResponse::numberToLabel).toList()
                : List.of();
        return GameStateResponse.builder()
                .gameId(state.getGameId())
                .status(state.getStatus())
                .currentCallIndex(state.getCurrentCallIndex())
                .totalNumbersCalled(state.getTotalNumbersCalled())
                .calledNumbers(state.getCalledNumbers())
                .calledNumbersLabeled(labeled)
                .winningPattern(state.getWinningPattern())
                .fairnessHash(state.getFairnessHash())
                .prizePool(state.getPrizePool())
                .playerCard(state.getPlayerCard())
                .autoMark(Boolean.TRUE.equals(state.getAutoMark()))
                .commissionPercent(state.getCommissionPercent())
                .markedNumbers(state.getMarkedNumbers())
                .hasPlayerCard(state.isHasPlayerCard())
                .isWinner(state.isWinner())
                .isBanned(state.isBanned())
                .startTime(state.getStartTime())
                .build();
    }

    public AdminGameStateResponse toAdminGameStateDto(GameEngineService.AdminGameState state) {
        if (state == null) return null;
        Game game = state.getGame();
        List<String> labeled = state.getCalledNumbers() != null
                ? state.getCalledNumbers().stream().map(GameStateResponse::numberToLabel).toList()
                : List.of();
        return AdminGameStateResponse.builder()
                .gameId(game.getId())
                .status(game.getStatus())
                .entryFee(game.getEntryFee())
                .maxPlayers(game.getMaxPlayers())
                .currentCallIndex(game.getCurrentCallIndex())
                .totalNumbersCalled(game.getTotalNumbersCalled())
                .prizePool(game.getPrizePool())
                .winningPattern(game.getWinningPattern())
                .callInterval(game.getCallInterval())
                .commissionPercent(game.getCommissionPercent())
                .autoMark(Boolean.TRUE.equals(game.getAutoMark()))
                .startTime(game.getStartTime())
                .endTime(game.getEndTime())
                .createdAt(game.getCreatedAt())
                .calledNumbers(state.getCalledNumbers())
                .calledNumbersLabeled(labeled)
                .playerCount(state.getPlayerCount())
                .build();
    }

    public WalletResponse toWalletDto(User user) {
        if (user == null) return null;
        return WalletResponse.builder()
                .balance(user.getBalance())
                .frozenBalance(user.getFrozenBalance())
                .build();
    }
}
