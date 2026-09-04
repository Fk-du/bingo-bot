package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.Withdrawal;
import com.bingo.app.tenant.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    List<Withdrawal> findByUserIdAndStatus(Long userId, RequestStatus status);

    List<Withdrawal> findByUserIdInAndStatus(List<Long> userIds, RequestStatus status);

    List<Withdrawal> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds);

    List<Withdrawal> findByStatus(RequestStatus status);

    @Query("SELECT COUNT(w) FROM Withdrawal w WHERE w.userId IN :userIds AND w.status = :status")
    long countByUserIdInAndStatus(@Param("userIds") List<Long> userIds, @Param("status") RequestStatus status);

    List<Withdrawal> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Atomically moves a PENDING withdrawal to the target status. Returns 0 when the
     * withdrawal was already processed, which makes concurrent/double approvals impossible.
     */
    @Modifying
    @Query("UPDATE Withdrawal w SET w.status = :status, w.processedBy = :processedBy, " +
            "w.processedAt = :processedAt, w.rejectionReason = :rejectionReason " +
            "WHERE w.id = :id AND w.status = :pending")
    int claimForProcessing(@Param("id") Long id,
                           @Param("status") RequestStatus status,
                           @Param("pending") RequestStatus pending,
                           @Param("processedBy") Long processedBy,
                           @Param("processedAt") LocalDateTime processedAt,
                           @Param("rejectionReason") String rejectionReason);
}
