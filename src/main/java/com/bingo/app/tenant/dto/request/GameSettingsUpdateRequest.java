package com.bingo.app.tenant.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record GameSettingsUpdateRequest(
        Integer maxPlayers,
        Integer callInterval,
        String winningPattern,
        @DecimalMin(value = "0", message = "Commission cannot be negative")
        @DecimalMax(value = "90", message = "Commission cannot exceed 90%")
        BigDecimal commissionPercent
) {}
