package com.bingo.app.master.dto.response;

import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.dto.response.PlayerResponse;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record DashboardSummaryResponse(
        long totalPlayers,
        List<PlayerResponse> recentPlayers,
        long totalGames,
        long pendingClaimsCount,
        List<GameResponse> recentGames,
        long pendingCoinRequests,
        long pendingWithdrawals,
        long pendingFundRequests,
        BigDecimal balance
) {}
