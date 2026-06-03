package com.bingo.app.master.repository;

import com.bingo.app.master.entity.TenantRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRegistryRepository extends JpaRepository<TenantRegistry, Long> {

    Optional<TenantRegistry> findByAgentId(Long agentId);

    boolean existsByAgentId(Long agentId);

    Optional<TenantRegistry> findByDatabaseName(String databaseName);
}