package com.bingo.app.master.dto.response;

import java.math.BigDecimal;

public record AgentStatsResponse(
        long totalGames,
        long endedGames,
        long totalPlayers,
        long totalTransactions,
        BigDecimal totalCommission,
        BigDecimal balance
) {}
