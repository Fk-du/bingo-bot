package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CalledNumberResponse(
        Long id,
        Long gameId,
        Integer number,
        Integer sequenceIndex,
        LocalDateTime calledAt
) {}
