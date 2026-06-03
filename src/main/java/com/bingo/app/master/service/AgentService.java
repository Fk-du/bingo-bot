package com.bingo.app.master.service;

import com.bingo.app.master.entity.Agent;
import com.bingo.app.master.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;

    @Transactional
    public Agent registerAgent(Long userId, String businessName) {
        Agent agent = Agent.builder()
                .userId(userId)
                .businessName(businessName)
                .approved(false)
                .active(true)
                .build();

        return agentRepository.save(agent);
    }

    @Transactional
    public Agent approveAgent(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        agent.setApproved(true);
        return agentRepository.save(agent);
    }
}