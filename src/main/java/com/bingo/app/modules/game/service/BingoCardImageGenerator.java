package com.bingo.app.modules.game.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class BingoCardImageGenerator {

    private static final int CELL_SIZE = 90;
    private static final int HEADER_HEIGHT = 50;
    private static final int PADDING = 20;
    private static final int WIDTH = CELL_SIZE * 5 + PADDING * 2;
    private static final int HEIGHT = HEADER_HEIGHT + CELL_SIZE * 5 + PADDING * 2;
    private static final String[] HEADERS = {"B", "I", "N", "G", "O"};
    private static final String FREE = "FREE";

    private static final Color HEADER_BG = new Color(46, 139, 87);
    private static final Color FREE_BG = new Color(255, 215, 0);
    private static final Color GRID_COLOR = Color.BLACK;
    private static final Color BG_COLOR = Color.WHITE;
    private static final Color TEXT_COLOR = Color.BLACK;

    public byte[] generatePng(String numbersCsv) {
        String[][] grid = buildGrid(numbersCsv);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(BG_COLOR);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            drawHeaders(g);
            drawGrid(g, grid);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate bingo card image", e);
        } finally {
            g.dispose();
        }
    }

    private void drawHeaders(Graphics2D g) {
        g.setFont(new Font("Arial", Font.BOLD, 28));
        FontMetrics fm = g.getFontMetrics();

        for (int col = 0; col < 5; col++) {
            int x = PADDING + col * CELL_SIZE;
            int y = PADDING;

            g.setColor(HEADER_BG);
            g.fillRect(x, y, CELL_SIZE, HEADER_HEIGHT);

            g.setColor(GRID_COLOR);
            g.drawRect(x, y, CELL_SIZE, HEADER_HEIGHT);

            g.setColor(BG_COLOR);
            String text = HEADERS[col];
            int textX = x + (CELL_SIZE - fm.stringWidth(text)) / 2;
            int textY = y + (HEADER_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(text, textX, textY);
        }
    }

    private void drawGrid(Graphics2D g, String[][] grid) {
        g.setFont(new Font("Arial", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int x = PADDING + col * CELL_SIZE;
                int y = PADDING + HEADER_HEIGHT + row * CELL_SIZE;

                boolean isFree = FREE.equals(grid[row][col]);
                g.setColor(isFree ? FREE_BG : BG_COLOR);
                g.fillRect(x, y, CELL_SIZE, CELL_SIZE);

                g.setColor(GRID_COLOR);
                g.drawRect(x, y, CELL_SIZE, CELL_SIZE);

                g.setColor(TEXT_COLOR);
                String text = grid[row][col];
                int textX = x + (CELL_SIZE - fm.stringWidth(text)) / 2;
                int textY = y + (CELL_SIZE + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(text, textX, textY);
            }
        }
    }

    private String[][] buildGrid(String numbersCsv) {
        String[][] grid = new String[5][5];
        List<String> tokens = parseTokens(numbersCsv);

        if (tokens.size() != 25) {
            throw new IllegalArgumentException("Card must have 25 cells, got " + tokens.size());
        }

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                grid[row][col] = tokens.get(row * 5 + col);
            }
        }

        return grid;
    }

    private List<String> parseTokens(String rawNumbers) {
        if (rawNumbers == null || rawNumbers.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(rawNumbers.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
    }
}
