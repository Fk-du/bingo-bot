package com.bingo.app.tenant.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record GameSettingsUpdateRequest(
        @Min(value = 2, message = "At least 2 players required")
        @Max(value = 100, message = "Cannot exceed 100 players")
        Integer maxPlayers,
        @Min(value = 2, message = "Call interval must be at least 2 seconds")
        @Max(value = 300, message = "Call interval cannot exceed 300 seconds")
        Integer callInterval,
        String winningPattern,
        @DecimalMin(value = "1", message = "Commission must be at least 1%")
        @DecimalMax(value = "90", message = "Commission cannot exceed 90%")
        BigDecimal commissionPercent,
        Boolean autoMark
) {
}
