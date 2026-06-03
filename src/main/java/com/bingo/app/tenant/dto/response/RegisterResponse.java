package com.bingo.app.tenant.dto.response;

import lombok.Builder;

@Builder
public record RegisterResponse(
        Long gameId,
        Long cardId
) {}
