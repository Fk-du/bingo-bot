package com.bingo.app.modules.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "winners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Winner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gameId;

    private Long playerId;

    private Long cardId;

    private BigDecimal rewardAmount;
}
