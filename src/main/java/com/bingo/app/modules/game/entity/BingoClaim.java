package com.bingo.app.modules.game.entity;

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

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private Long cardId;

    @Column(columnDefinition = "TEXT")
    private String cardSnapshot;

    @Column(columnDefinition = "TEXT")
    private String calledNumbersSnapshot;

    @Column(nullable = false)
    private String result;

    private BigDecimal rewardAmount;

    @Builder.Default
    private LocalDateTime claimedAt = LocalDateTime.now();

    private LocalDateTime validatedAt;
}
