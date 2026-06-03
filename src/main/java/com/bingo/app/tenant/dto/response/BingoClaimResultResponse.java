package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BingoClaimResultResponse(
        boolean valid,
        BigDecimal rewardAmount,
        BigDecimal platformFee,
        BigDecimal agentCommission
) {}
