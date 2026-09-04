package com.bingo.app.master.dto.response;

import java.time.LocalDateTime;

public record AdminWarningResponse(
        Long id,
        Long adminUserId,
        String reason,
        Long createdBy,
        LocalDateTime createdAt
) {}
