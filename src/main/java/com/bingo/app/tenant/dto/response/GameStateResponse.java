package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record GameStateResponse(
        Long gameId,
        GameStatus status,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        List<Integer> calledNumbers,
        BigDecimal prizePool,
        int[][] playerCard,
        boolean hasPlayerCard,
        boolean isWinner
) {}
