package com.bingo.app.tenant.dto.request;

import lombok.Data;

import java.util.List;

/**
 * Optional body for POST /games/{id}/claim.
 * When the game has auto-mark disabled the client must send the numbers
 * the player daubed on their card; the server verifies them against the
 * called numbers before accepting the claim.
 */
@Data
public class ClaimBingoRequest {
    private List<Integer> markedNumbers;
    /**
     * The player's own auto-mark preference for this game (null = not changing).
     * Sent with the marks endpoint to persist, and with the claim endpoint to control validation.
     */
    private Boolean autoMark;
}
