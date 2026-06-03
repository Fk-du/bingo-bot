package com.bingo.app.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cards", indexes = {
        @Index(name = "idx_cards_used", columnList = "used"),
        @Index(name = "idx_cards_hash", columnList = "numbers_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numbers", columnDefinition = "TEXT", nullable = false)
    private String numbers;

    @Column(name = "numbers_hash", unique = true, length = 64)
    private String numbersHash;

    @Builder.Default
    private boolean used = false;

    @Builder.Default
    @Column(name = "usage_count")
    private Integer usageCount = 0;

    @Builder.Default
    @Column(name = "win_rate")
    private Double winRate = 0.0;

    @Version
    private Long version;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}