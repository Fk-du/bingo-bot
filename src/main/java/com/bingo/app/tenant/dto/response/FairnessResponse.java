package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.util.List;

/**
 * Commit-reveal fair-play proof for a game. The hash is published before the
 * first number is called; the sealed call order is revealed once the game is
 * over so anyone can recompute the hash and confirm nothing was altered.
 */
@Builder
public record FairnessResponse(
        Long gameId,
        GameStatus status,
        String algorithm,
        String fairnessHash,
        boolean revealed,
        boolean sequenceIntact,
        List<Integer> sequence,
        Integer calledCount,
        Integer totalNumbersCalled
) {}
