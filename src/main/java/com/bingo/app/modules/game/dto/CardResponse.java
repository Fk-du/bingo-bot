package com.bingo.app.modules.game.dto;

import com.bingo.app.modules.game.entity.Card;

public record CardResponse(
    Long id,
    String numbers,
    boolean used
) {
    public static CardResponse from(Card card) {
        return new CardResponse(
            card.getId(),
            card.getNumbers(),
            card.isUsed()
        );
    }
}
