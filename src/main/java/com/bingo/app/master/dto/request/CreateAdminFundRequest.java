package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateAdminFundRequest(
        @Positive BigDecimal amount,
        @NotBlank String screenshotUrl
) {}
