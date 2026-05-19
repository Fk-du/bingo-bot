package com.bingo.app.modules.game.dto;

import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.modules.game.entity.Game;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GameResponse(
    Long id,
    Long adminId,
    GameStatus status,
    BigDecimal entryFee,
    Integer maxPlayers,
    Integer currentCallIndex,
    LocalDateTime startTime,
    LocalDateTime endTime,
    LocalDateTime createdAt
) {
    public static GameResponse from(Game game) {
        return new GameResponse(
            game.getId(),
            game.getAdminId(),
            game.getStatus(),
            game.getEntryFee(),
            game.getMaxPlayers(),
            game.getCurrentCallIndex(),
            game.getStartTime(),
            game.getEndTime(),
            game.getCreatedAt()
        );
    }
}
