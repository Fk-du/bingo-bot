package com.bingo.app.modules.game.dto;

import com.bingo.app.modules.game.entity.Winner;

import java.math.BigDecimal;

public record WinnerResponse(
    Long id,
    Long gameId,
    Long playerId,
    Long cardId,
    BigDecimal rewardAmount
) {
    public static WinnerResponse from(Winner winner) {
        return new WinnerResponse(
            winner.getId(),
            winner.getGameId(),
            winner.getPlayerId(),
            winner.getCardId(),
            winner.getRewardAmount()
        );
    }
}
