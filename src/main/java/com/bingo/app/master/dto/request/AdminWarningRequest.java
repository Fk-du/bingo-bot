package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminWarningRequest(
        @NotBlank String reason
) {}
