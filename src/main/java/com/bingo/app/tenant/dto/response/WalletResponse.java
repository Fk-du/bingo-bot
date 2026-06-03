package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record WalletResponse(
        BigDecimal balance,
        BigDecimal frozenBalance
) {}
