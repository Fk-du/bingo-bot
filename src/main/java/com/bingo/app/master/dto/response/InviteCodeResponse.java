package com.bingo.app.master.dto.response;

import com.bingo.app.master.enums.Role;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record InviteCodeResponse(
        Long id,
        String code,
        Long creatorId,
        Role role,
        boolean active,
        LocalDateTime createdAt
) {}
