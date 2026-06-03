package com.bingo.app.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "called_numbers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_game_sequence", columnNames = {"game_id", "sequence_index"})
        },
        indexes = {
                @Index(name = "idx_called_numbers_game", columnList = "game_id"),
                @Index(name = "idx_called_numbers_game_called", columnList = "game_id, called_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalledNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "sequence_index", nullable = false)
    private Integer sequenceIndex;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    // Helper method to check if number has been called
    public boolean isCalled() {
        return calledAt != null;
    }

    // Helper method to mark number as called
    public void markAsCalled() {
        this.calledAt = LocalDateTime.now();
    }
}