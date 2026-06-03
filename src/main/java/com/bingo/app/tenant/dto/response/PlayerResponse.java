package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PlayerResponse(
        Long id,
        Long userId,
        Long agentId,
        Long parentId,
        LocalDateTime createdAt
) {}
