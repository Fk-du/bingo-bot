package com.bingo.app.modules.game.dto;

import com.bingo.app.modules.game.entity.BingoClaim;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BingoClaimResponse(
    Long id,
    Long gameId,
    Long playerId,
    Long cardId,
    String cardSnapshot,
    String calledNumbersSnapshot,
    String result,
    BigDecimal rewardAmount,
    LocalDateTime claimedAt,
    LocalDateTime validatedAt
) {
    public static BingoClaimResponse from(BingoClaim claim) {
        return new BingoClaimResponse(
            claim.getId(),
            claim.getGameId(),
            claim.getPlayerId(),
            claim.getCardId(),
            claim.getCardSnapshot(),
            claim.getCalledNumbersSnapshot(),
            claim.getResult(),
            claim.getRewardAmount(),
            claim.getClaimedAt(),
            claim.getValidatedAt()
        );
    }
}
