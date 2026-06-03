package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.TransactionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record TransactionResponse(
        Long id,
        Long userId,
        String type,
        BigDecimal amount,
        TransactionStatus status,
        Long referenceId,
        String description,
        LocalDateTime createdAt
) {}
