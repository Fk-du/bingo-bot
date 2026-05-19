package com.bingo.app.modules.topup.entity;

import com.bingo.app.modules.topup.enums.TopUpStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "topup_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopUpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long requesterId;

    private Long approverId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TopUpStatus status;

    private String proofImageFileId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;
}
