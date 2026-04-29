package com.bingo.app.modules.game.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Store bingo numbers as JSON string or use converter later
    @Column(columnDefinition = "TEXT")
    private String numbers;

    @Builder.Default
    private boolean used = false;
    
}
