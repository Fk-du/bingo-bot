package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.Withdrawal;
import com.bingo.app.tenant.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    List<Withdrawal> findByUserIdAndStatus(Long userId, RequestStatus status);

    List<Withdrawal> findByUserIdInAndStatus(List<Long> userIds, RequestStatus status);

    List<Withdrawal> findByStatus(RequestStatus status);
}