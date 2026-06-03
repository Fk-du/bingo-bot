package com.bingo.app.tenant.dto.request;

public record GameSettingsUpdateRequest(
        Integer maxPlayers,
        Integer callInterval,
        String winningPattern
) {}
