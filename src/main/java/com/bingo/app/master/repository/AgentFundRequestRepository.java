package com.bingo.app.master.repository;

import com.bingo.app.master.entity.AgentFundRequest;
import com.bingo.app.master.enums.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentFundRequestRepository extends JpaRepository<AgentFundRequest, Long> {

    List<AgentFundRequest> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    List<AgentFundRequest> findByStatusOrderByCreatedAtDesc(FundStatus status);

    List<AgentFundRequest> findAllByOrderByCreatedAtDesc();
}
