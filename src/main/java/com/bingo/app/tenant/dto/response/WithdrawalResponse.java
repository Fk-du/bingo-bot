package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.RequestStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record WithdrawalResponse(
        Long id,
        Long userId,
        BigDecimal amount,
        String payoutMethod,
        String payoutDetails,
        RequestStatus status,
        Long processedBy,
        LocalDateTime processedAt,
        String rejectionReason,
        LocalDateTime createdAt
) {}
