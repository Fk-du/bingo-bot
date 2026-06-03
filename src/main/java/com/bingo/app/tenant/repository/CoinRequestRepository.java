package com.bingo.app.tenant.repository;


import com.bingo.app.tenant.entity.CoinRequest;
import com.bingo.app.tenant.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoinRequestRepository extends JpaRepository<CoinRequest, Long> {

    List<CoinRequest> findByUserIdAndStatus(Long userId, RequestStatus status);

    List<CoinRequest> findByUserIdInAndStatus(List<Long> userIds, RequestStatus status);

    List<CoinRequest> findByStatus(RequestStatus status);
}