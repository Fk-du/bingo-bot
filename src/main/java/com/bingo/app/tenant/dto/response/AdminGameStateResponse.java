package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record AdminGameStateResponse(
        Long gameId,
        GameStatus status,
        BigDecimal entryFee,
        Integer maxPlayers,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        BigDecimal prizePool,
        String winningPattern,
        Integer callInterval,
        BigDecimal commissionPercent,
        boolean autoMark,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        List<Integer> calledNumbers,
        List<String> calledNumbersLabeled,
        int playerCount
) {}
