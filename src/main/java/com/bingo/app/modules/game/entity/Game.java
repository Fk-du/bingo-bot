package com.bingo.app.modules.game.entity;

import com.bingo.app.modules.game.enums.GameStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long adminId;

    @Enumerated(EnumType.STRING)
    private GameStatus status;

    private BigDecimal entryFee;

    private Integer maxPlayers;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Builder.Default
    private Integer currentCallIndex = 0;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PreUpdate
    @PrePersist
    public void updateEndTime() {
        if (this.status == GameStatus.ENDED && this.endTime == null) {
            this.endTime = LocalDateTime.now();
        }
    }

}
