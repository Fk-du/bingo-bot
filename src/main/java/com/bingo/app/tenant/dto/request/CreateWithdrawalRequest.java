package com.bingo.app.tenant.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateWithdrawalRequest(
        @Positive BigDecimal amount,
        @NotBlank String payoutDetails
) {}
