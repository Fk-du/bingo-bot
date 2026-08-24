package com.bingo.app.tenant.enums;

public enum TransactionType {
    TOP_UP,              // Player buying coins
    WITHDRAWAL,          // Player withdrawing coins
    DEPOSIT,             // Agent or player receiving coins
    BET,                 // Player placing bet
    WIN,                 // Player winning
    PLATFORM_FEE,        // Platform fee deduction
    AGENT_COMMISSION,    // Agent commission from game winnings
    FUND_AGENT_TO_PLAYER,// Agent funding player
    FUND_SUPER_ADMIN_TO_AGENT, // Super admin funding agent
    REFUND,              // Entry fee refund on cancelled/ended game
    UNCLAIMED_PRIZE      // Unclaimed winner-slot share swept to the agent on game end
}