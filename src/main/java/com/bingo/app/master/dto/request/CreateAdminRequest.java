package com.bingo.app.master.dto.request;

public record CreateAdminRequest(
        Long creatorId,
        Long telegramId,
        String username,
        String firstName,
        String lastName
) {}
