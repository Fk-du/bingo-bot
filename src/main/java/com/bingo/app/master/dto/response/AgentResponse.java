package com.bingo.app.master.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AgentResponse(
        Long id,
        Long userId,
        String businessName,
        boolean approved,
        boolean active,
        LocalDateTime createdAt
) {}
