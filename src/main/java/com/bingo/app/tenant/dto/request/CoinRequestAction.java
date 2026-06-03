package com.bingo.app.tenant.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CoinRequestAction(
        @NotBlank String action,
        String reason
) {}
