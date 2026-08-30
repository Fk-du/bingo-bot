package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record PendingClaimCardResponse(
        Long claimId,
        Long playerId,
        String playerName,
        int[][] cardNumbers,
        List<Integer> calledNumbers
) {}
