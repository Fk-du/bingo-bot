package com.bingo.app.tenant.service;

import com.bingo.app.tenant.entity.GameCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for the bingo engine's money and win-detection rules.
 * No Spring context — runs in milliseconds.
 */
class GameEngineLogicTest {

    private final GameEngineService engine = new GameEngineService(
            null, null, null, null, null, null, null, null, null, null, null
    );

    // ===== cent-perfect equal splitting =====

    @Test
    @DisplayName("splitEvenly: exact division")
    void splitExact() {
        BigDecimal[] shares = engine.splitEvenly(new BigDecimal("18.00"), 2);
        assertAll(
                () -> assertEquals(new BigDecimal("9.00"), shares[0]),
                () -> assertEquals(new BigDecimal("9.00"), shares[1]),
                () -> assertEquals(0, new BigDecimal("18.00").compareTo(shares[0].add(shares[1])))
        );
    }

    @Test
    @DisplayName("splitEvenly: remainder absorbed by earlier winners, total conserved")
    void splitRounding() {
        BigDecimal[] shares = engine.splitEvenly(new BigDecimal("10.00"), 3);
        assertEquals(0, new BigDecimal("10.00").compareTo(shares[0].add(shares[1]).add(shares[2])));
        assertTrue(shares[0].compareTo(shares[2]) >= 0, "earlier winners absorb the rounding");
        assertEquals(new BigDecimal("3.34"), shares[0]);
        assertEquals(new BigDecimal("3.33"), shares[1]);
        assertEquals(new BigDecimal("3.33"), shares[2]);
    }

    @Test
    @DisplayName("splitEvenly: single cent among two winners")
    void splitTinyAmounts() {
        BigDecimal[] shares = engine.splitEvenly(new BigDecimal("0.01"), 2);
        assertEquals(0, new BigDecimal("0.01").compareTo(shares[0].add(shares[1])));
    }

    // ===== pattern validation (mirrors server rules; free centre counts as called) =====

    private int[][] fullCard() {
        int n = 1;
        int[][] card = new int[5][5];
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                card[r][c] = (r == 2 && c == 2) ? 0 : n++;
        return card;
    }

    private List<Integer> cells(int[][] card, int[][] coords) {
        return java.util.Arrays.stream(coords).map(rc -> card[rc[0]][rc[1]]).toList();
    }

    @Test
    @DisplayName("SINGLE_LINE: any row completes; missing cell fails")
    void singleLine() {
        int[][] card = fullCard();
        List<Integer> topRow = cells(card, new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}});
        assertTrue(engine.validateBingo(card, topRow, "SINGLE_LINE"));
        assertTrue(engine.validateBingo(card, topRow.subList(0, 4), "SINGLE_LINE") == false);
    }

    @Test
    @DisplayName("DOUBLE_LINE: needs two distinct complete lines")
    void doubleLine() {
        int[][] card = fullCard();
        List<Integer> oneRow = cells(card, new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}});
        List<Integer> twoRows = cells(card, new int[][]{
                {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4},
                {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}});
        assertFalse(engine.validateBingo(card, oneRow, "DOUBLE_LINE"));
        assertTrue(engine.validateBingo(card, twoRows, "DOUBLE_LINE"));
    }

    @Test
    @DisplayName("FULL_HOUSE / BLACKOUT: all 24 numbers required")
    void fullHouse() {
        int[][] card = fullCard();
        List<Integer> all = cells(card, java.util.Arrays.stream(
                new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4},
                        {1, 0}, {1, 1}, {1, 2}, {1, 3}, {1, 4},
                        {2, 0}, {2, 1}, {2, 2}, {2, 3}, {2, 4},
                        {3, 0}, {3, 1}, {3, 2}, {3, 3}, {3, 4},
                        {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}}).toArray(int[][]::new));
        assertTrue(engine.validateBingo(card, all, "FULL_HOUSE"));
        assertTrue(engine.validateBingo(card, all, "BLACKOUT"));
        assertFalse(engine.validateBingo(card, all.subList(0, 23), "FULL_HOUSE"));
    }

    @Test
    @DisplayName("FOUR_CORNERS: only corners matter")
    void fourCorners() {
        int[][] card = fullCard();
        List<Integer> corners = cells(card, new int[][]{{0, 0}, {0, 4}, {4, 0}, {4, 4}});
        assertTrue(engine.validateBingo(card, corners, "FOUR_CORNERS"));
        assertFalse(engine.validateBingo(card, corners.subList(0, 3), "FOUR_CORNERS"));
    }

    @Test
    @DisplayName("X_SHAPE: both diagonals through the free centre")
    void xShape() {
        int[][] card = fullCard();
        List<Integer> x = cells(card, new int[][]{{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}, {0, 4}, {1, 3}, {3, 1}, {4, 0}});
        assertTrue(engine.validateBingo(card, x, "X_SHAPE"));
        assertFalse(engine.validateBingo(card, x.subList(0, 8), "X_SHAPE"));
    }

    @Test
    @DisplayName("L_SHAPE: first column plus bottom row")
    void lShape() {
        int[][] card = fullCard();
        List<Integer> l = cells(card, new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}});
        assertTrue(engine.validateBingo(card, l, "L_SHAPE"));
        List<Integer> missingToe = l.subList(0, l.size() - 1);
        assertFalse(engine.validateBingo(card, missingToe, "L_SHAPE"));
    }

    @Test
    @DisplayName("T_SHAPE: top row plus middle column")
    void tShape() {
        int[][] card = fullCard();
        List<Integer> t = cells(card, new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 2}, {2, 2}, {3, 2}, {4, 2}});
        assertTrue(engine.validateBingo(card, t, "T_SHAPE"));
        List<Integer> missingStem = t.subList(0, t.size() - 1);
        assertFalse(engine.validateBingo(card, missingStem, "T_SHAPE"));
    }

    @Test
    @DisplayName("POSTAGE_STAMP: any corner 2x2 block")
    void postageStamp() {
        int[][] card = fullCard();
        List<Integer> topLeft = cells(card, new int[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}});
        List<Integer> bottomRight = cells(card, new int[][]{{3, 3}, {3, 4}, {4, 3}, {4, 4}});
        assertTrue(engine.validateBingo(card, topLeft, "POSTAGE_STAMP"));
        assertTrue(engine.validateBingo(card, bottomRight, "POSTAGE_STAMP"));
        assertFalse(engine.validateBingo(card, cells(card, new int[][]{{2, 2}, {2, 3}, {3, 2}, {3, 3}}), "POSTAGE_STAMP"));
    }

    // ===== persisted manual daubs =====

    @Test
    @DisplayName("parseMarkedNumbers: blank/null/invalid handled safely")
    void parseMarks() {
        GameCard card = new GameCard();
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(null));
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(card));
        card.setMarkedNumbers("  ");
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(card));
        card.setMarkedNumbers("7, 12 ,0,44");
        assertEquals(List.of(7, 12, 0, 44), GameEngineService.parseMarkedNumbers(card));
        card.setMarkedNumbers("7,oops");
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(card));
    }
}
