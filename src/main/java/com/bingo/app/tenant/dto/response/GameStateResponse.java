package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record GameStateResponse(
        Long gameId,
        GameStatus status,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        List<Integer> calledNumbers,
        List<String> calledNumbersLabeled,
        String winningPattern,
        boolean autoMark,
        java.math.BigDecimal commissionPercent,
        java.util.List<Integer> markedNumbers,
        String fairnessHash,
        BigDecimal prizePool,
        int[][] playerCard,
        boolean hasPlayerCard,
        boolean isWinner,
        boolean isBanned,
        LocalDateTime startTime
) {
    public static String numberToLabel(Integer number) {
        if (number == null || number < 1 || number > 75) return String.valueOf(number);
        return switch ((number - 1) / 15) {
            case 0 -> "B" + number;
            case 1 -> "I" + number;
            case 2 -> "N" + number;
            case 3 -> "G" + number;
            case 4 -> "O" + number;
            default -> String.valueOf(number);
        };
    }
}
