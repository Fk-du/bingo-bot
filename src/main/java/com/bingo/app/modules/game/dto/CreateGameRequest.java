package com.bingo.app.modules.game.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateGameRequest(
    @NotNull @DecimalMin("10") BigDecimal entryFee,
    @Max(500) Integer maxPlayers
) {}
