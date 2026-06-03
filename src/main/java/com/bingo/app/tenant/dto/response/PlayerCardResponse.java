package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.AssignmentStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PlayerCardResponse(
        Long id,
        Long playerId,
        CardResponse card,
        AssignmentStatus status,
        Integer gamesPlayed,
        Integer gamesWon,
        LocalDateTime assignedAt,
        LocalDateTime unassignedAt
) {}
