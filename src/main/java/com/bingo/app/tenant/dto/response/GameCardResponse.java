package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record GameCardResponse(
        Long id,
        Long gameId,
        Long playerId,
        CardResponse card,
        boolean winner,
        LocalDateTime createdAt
) {}
