package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AuditLogResponse(
        Long id,
        Long userId,
        String action,
        String details,
        String ipAddress,
        LocalDateTime createdAt
) {}
