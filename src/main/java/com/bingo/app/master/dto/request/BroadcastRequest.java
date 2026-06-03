package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BroadcastRequest(
        @NotBlank String target,
        @NotBlank String message
) {}
