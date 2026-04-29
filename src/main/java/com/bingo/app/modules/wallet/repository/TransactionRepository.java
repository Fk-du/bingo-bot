package com.bingo.app.modules.wallet.repository;


import com.bingo.app.modules.wallet.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.bingo.app.modules.wallet.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByUserIdAndStatus(Long userId, TransactionStatus status);
}
