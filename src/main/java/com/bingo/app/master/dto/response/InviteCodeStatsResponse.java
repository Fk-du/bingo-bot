package com.bingo.app.master.dto.response;

import lombok.Builder;

@Builder
public record InviteCodeStatsResponse(
        long totalCodes,
        long activeCodes,
        long usedCodes,
        long totalRegistrations
) {}
