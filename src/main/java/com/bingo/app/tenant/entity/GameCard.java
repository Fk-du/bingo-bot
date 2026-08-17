package com.bingo.app.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_cards", indexes = {
        @Index(name = "idx_game_cards_game", columnList = "game_id"),
        @Index(name = "idx_game_cards_player", columnList = "player_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameCard {

    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id")
    private Long gameId;
    @Column(name = "player_id")
    private Long playerId;

    @ManyToOne
    @JoinColumn(name = "card_id")
    private Card card;

    @Builder.Default
    private boolean winner = false;

    @Builder.Default
    private boolean banned = false;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}