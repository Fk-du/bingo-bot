package com.bingo.app.tenant.repository;


import com.bingo.app.tenant.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Transaction> findAllByOrderByCreatedAtDesc();

    Optional<Transaction> findByReferenceIdAndType(Long referenceId, String type);

    List<Transaction> findByUserIdAndType(Long userId, String type);

    List<Transaction> findByType(String type);

    List<Transaction> findAllByReferenceIdAndType(Long referenceId, String type);
}