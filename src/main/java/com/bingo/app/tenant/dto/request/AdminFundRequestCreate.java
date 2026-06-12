package com.bingo.app.tenant.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AdminFundRequestCreate(
        @Positive BigDecimal amount,
        String screenshotUrl
) {}
