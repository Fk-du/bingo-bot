package com.bingo.app.modules.game.dto;

import com.bingo.app.modules.game.entity.GameCard;

public record GameCardResponse(
    Long id,
    Long gameId,
    Long playerId,
    Long cardId,
    boolean winner
) {
    public static GameCardResponse from(GameCard gc) {
        return new GameCardResponse(
            gc.getId(),
            gc.getGameId(),
            gc.getPlayerId(),
            gc.getCardId(),
            gc.isWinner()
        );
    }
}
