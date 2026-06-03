package com.bingo.app.tenant.entity;

import com.bingo.app.tenant.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_cards", uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_active", columnNames = {"player_id", "status"})
}, indexes = {
        @Index(name = "idx_player_cards_player", columnList = "player_id"),
        @Index(name = "idx_player_cards_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id")
    private Long playerId;

    @ManyToOne
    @JoinColumn(name = "card_id")
    private Card card;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status;

    @Builder.Default
    @Column(name = "games_played")
    private Integer gamesPlayed = 0;

    @Builder.Default
    @Column(name = "games_won")
    private Integer gamesWon = 0;

    @Builder.Default
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;
}