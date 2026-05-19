package com.bingo.app.modules.game.dto;

import com.bingo.app.modules.game.entity.CalledNumber;

import java.time.LocalDateTime;

public record CalledNumberResponse(
    Long id,
    Long gameId,
    Integer number,
    Integer sequenceIndex,
    LocalDateTime calledAt
) {
    public static CalledNumberResponse from(CalledNumber cn) {
        return new CalledNumberResponse(
            cn.getId(),
            cn.getGameId(),
            cn.getNumber(),
            cn.getSequenceIndex(),
            cn.getCalledAt()
        );
    }
}
