package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.RequestStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record CoinRequestResponse(
        Long id,
        Long userId,
        BigDecimal amount,
        String screenshotUrl,
        RequestStatus status,
        Long approvedBy,
        LocalDateTime approvedAt,
        String rejectionReason,
        LocalDateTime createdAt
) {}
