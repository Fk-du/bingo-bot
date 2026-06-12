package com.bingo.app.master.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record TenantRegistryResponse(
        Long id,
        Long adminUserId,
        String databaseName,
        LocalDateTime createdAt
) {}
