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
        Integer callInterval,
        BigDecimal commissionPercent,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        Boolean registered
) {}
