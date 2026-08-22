package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateAdminFundRequest(
        @Positive BigDecimal amount,
        String screenshotUrl
) {}
