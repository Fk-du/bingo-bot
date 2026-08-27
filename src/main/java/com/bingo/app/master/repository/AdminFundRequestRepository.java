package com.bingo.app.master.repository;

import com.bingo.app.master.entity.AdminFundRequest;
import com.bingo.app.master.enums.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdminFundRequestRepository extends JpaRepository<AdminFundRequest, Long> {

    List<AdminFundRequest> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);

    List<AdminFundRequest> findByStatusOrderByCreatedAtDesc(FundStatus status);

    long countByAdminUserIdAndStatus(Long adminUserId, FundStatus status);

    List<AdminFundRequest> findAllByOrderByCreatedAtDesc();

    /**
     * Atomically moves a PENDING request to the target status. Returns 0 when the
     * request was already processed, which makes concurrent/double approvals impossible.
     */
    @Modifying
    @Transactional(transactionManager = "masterTransactionManager")
    @Query("UPDATE AdminFundRequest r SET r.status = :status, r.approvedBy = :approvedBy, " +
            "r.approvedAt = :approvedAt, r.rejectionReason = :rejectionReason " +
            "WHERE r.id = :id AND r.status = :pending")
    int claimForProcessing(@Param("id") Long id,
                           @Param("status") FundStatus status,
                           @Param("pending") FundStatus pending,
                           @Param("approvedBy") Long approvedBy,
                           @Param("approvedAt") LocalDateTime approvedAt,
                           @Param("rejectionReason") String rejectionReason);
}
