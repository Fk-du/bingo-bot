package com.bingo.app.tenant.dto;

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
}