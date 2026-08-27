package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record PlayerResponse(
        Long id,
        Long userId,
        Long adminUserId,
        Long parentId,
        BigDecimal balance,
        BigDecimal frozenBalance,
        String firstName,
        String lastName,
        String username,
        LocalDateTime createdAt
) {}
