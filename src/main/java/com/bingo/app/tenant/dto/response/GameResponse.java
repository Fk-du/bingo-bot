package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder(toBuilder = true)
public record GameResponse(
        Long id,
        Long adminUserId,
        GameStatus status,
        BigDecimal entryFee,
        Integer maxPlayers,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        BigDecimal prizePool,
        String winningPattern,
        String customPatternName,
        String customPatternCells,
        Integer callInterval,
        BigDecimal commissionPercent,
        boolean autoMark,
        BigDecimal commissionEarned,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        Boolean registered,
        Long activeGameId
        , Integer registeredPlayers
) {}
