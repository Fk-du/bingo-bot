package com.bingo.app.tenant.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PlayerStatusRequest(
        @NotBlank String status
) {}
