package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BingoClaimResultResponse(
        boolean valid,
        Long claimId,
        boolean pendingReview,
        boolean gameEnded,
        int approvedCount,
        BigDecimal rewardAmount,
        BigDecimal platformFee,
        BigDecimal agentCommission
) {}