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

    /** Comma-separated numbers the player daubed manually (auto-mark games leave this null). */
    @Column(name = "marked_numbers")
    private String markedNumbers;

    /**
     * Per-player auto-mark preference (null = follow the game's setting).
     * True -> called numbers are highlighted automatically; False -> the player daubs.
     */
    @Column(name = "auto_mark")
    private Boolean autoMark;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}