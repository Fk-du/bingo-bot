package com.bingo.app.tenant.repository;


import com.bingo.app.tenant.entity.CoinRequest;
import com.bingo.app.tenant.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CoinRequestRepository extends JpaRepository<CoinRequest, Long> {

    List<CoinRequest> findByUserIdAndStatus(Long userId, RequestStatus status);

    List<CoinRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CoinRequest> findByUserIdInAndStatus(List<Long> userIds, RequestStatus status);

    List<CoinRequest> findByStatus(RequestStatus status);

    @Query("SELECT COUNT(c) FROM CoinRequest c WHERE c.userId IN :userIds AND c.status = :status")
    long countByUserIdInAndStatus(@Param("userIds") List<Long> userIds, @Param("status") RequestStatus status);

    /**
     * Atomically moves a PENDING request to the target status. Returns 0 when the
     * request was already processed, which makes concurrent/double approvals impossible.
     */
    @Modifying
    @Query("UPDATE CoinRequest c SET c.status = :status, c.approvedBy = :approvedBy, " +
            "c.approvedAt = :approvedAt, c.rejectionReason = :rejectionReason " +
            "WHERE c.id = :id AND c.status = :pending")
    int claimForProcessing(@Param("id") Long id,
                           @Param("status") RequestStatus status,
                           @Param("pending") RequestStatus pending,
                           @Param("approvedBy") Long approvedBy,
                           @Param("approvedAt") LocalDateTime approvedAt,
                           @Param("rejectionReason") String rejectionReason);
}
