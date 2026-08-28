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
    @Column(name = "custom_pattern_name")
    private String customPatternName;
    /** JSON array of [row,col] pairs for a custom pattern, e.g. [[0,0],[0,4]]. */
    @Column(name = "custom_pattern_cells", columnDefinition = "TEXT")
    private String customPatternCells;
    @Column(name = "call_interval")
    private Integer callInterval;
    @Column(name = "commission_percent")
    private BigDecimal commissionPercent = new BigDecimal("10.00");

    /** When false players must daub (mark) called numbers themselves before claiming. */
    private Boolean autoMark = true;
    @Column(name = "fairness_hash")
    private String fairnessHash;
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
