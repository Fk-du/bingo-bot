package com.bingo.app.tenant.entity;

import com.bingo.app.tenant.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;
    private BigDecimal amount;
    @Column(name = "payout_method")
    private String payoutMethod;
    @Column(name = "payout_details")
    private String payoutDetails;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @Column(name = "processed_by")
    private Long processedBy;
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}