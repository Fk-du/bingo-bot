package com.bingo.app.tenant.entity;

import com.bingo.app.tenant.enums.GameStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "games", indexes = {
        @Index(name = "idx_games_admin_status", columnList = "admin_user_id, status"),
        @Index(name = "idx_games_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    private GameStatus status;

    @Column(name = "entry_fee")
    private BigDecimal entryFee;
    @Column(name = "max_players")
    private Integer maxPlayers;
    @Column(name = "current_call_index")
    private Integer currentCallIndex;
    @Column(name = "total_numbers_called")
    private Integer totalNumbersCalled;
    @Column(name = "prize_pool")
    private BigDecimal prizePool;
    @Column(name = "winning_pattern")
    private String winningPattern;
    @Column(name = "call_interval")
    private Integer callInterval;
    @Column(name = "start_time")
    private LocalDateTime startTime;
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    private Long version;
}
