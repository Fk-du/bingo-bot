package com.bingo.app.modules.wallet.entity;

import com.bingo.app.modules.wallet.enums.TransactionStatus;
import com.bingo.app.modules.wallet.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private Long approvedBy;
    private String proofImageFileId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

}
