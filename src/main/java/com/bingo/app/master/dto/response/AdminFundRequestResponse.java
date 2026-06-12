package com.bingo.app.master.dto.response;

import com.bingo.app.master.enums.FundStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record AdminFundRequestResponse(
        Long id,
        Long adminUserId,
        BigDecimal amount,
        String screenshotUrl,
        FundStatus status,
        Long approvedBy,
        LocalDateTime approvedAt,
        String rejectionReason,
        LocalDateTime createdAt
) {}
