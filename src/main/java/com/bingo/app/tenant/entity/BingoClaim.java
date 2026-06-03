package com.bingo.app.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bingo_claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BingoClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id")
    private Long gameId;
    @Column(name = "player_id")
    private Long playerId;
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "card_snapshot", columnDefinition = "TEXT")
    private String cardSnapshot;

    @Column(name = "called_numbers_snapshot", columnDefinition = "TEXT")
    private String calledNumbersSnapshot;

    private String result;
    @Column(name = "reward_amount")
    private BigDecimal rewardAmount;

    @Builder.Default
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt = LocalDateTime.now();

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;
}