package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record BingoClaimResponse(
        Long id,
        Long gameId,
        Long playerId,
        Long cardId,
        String cardSnapshot,
        String calledNumbersSnapshot,
        String result,
        BigDecimal rewardAmount,
        Long validatedBy,
        String rejectionReason,
        LocalDateTime claimedAt,
        LocalDateTime validatedAt
) {}
