package com.bingo.app.master.dto.request;

public record CreatePlayerRequest(
        Long adminUserId,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        Long parentId
) {}
