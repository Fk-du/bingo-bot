package com.bingo.app.modules.topup.repository;

import com.bingo.app.modules.topup.entity.TopUpRequest;
import com.bingo.app.modules.topup.enums.TopUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopUpRequestRepository extends JpaRepository<TopUpRequest, Long> {

    List<TopUpRequest> findByApproverIdAndStatus(Long approverId, TopUpStatus status);

    List<TopUpRequest> findByRequesterId(Long requesterId);
}
