package com.bingo.app.modules.game.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gameId;

    private Long playerId;

    private Long cardId;

    @Builder.Default
    private boolean winner = false;
}
