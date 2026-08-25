package com.bingo.app.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGameRequest {
    @Positive
    private BigDecimal entryFee;
    @Min(value = 2, message = "At least 2 players required")
    @Max(value = 100, message = "Cannot exceed 100 players")
    private Integer maxPlayers;
    private String winningPattern;
    @Min(value = 2, message = "Call interval must be at least 2 seconds")
    @Max(value = 300, message = "Call interval cannot exceed 300 seconds")
    private Integer callInterval;
    @DecimalMin(value = "1", message = "Commission must be at least 1%")
    @DecimalMax(value = "90", message = "Commission cannot exceed 90%")
    private BigDecimal commissionPercent;

    /** Optional — defaults to true. When false, players mark numbers manually. */
    private Boolean autoMark;
}