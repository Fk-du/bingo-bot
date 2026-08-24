package com.bingo.app.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    private Integer maxPlayers;
    private String winningPattern;
    private Integer callInterval;
    @DecimalMin(value = "0", message = "Commission cannot be negative")
    @DecimalMax(value = "90", message = "Commission cannot exceed 90%")
    private BigDecimal commissionPercent;
}