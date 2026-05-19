package com.bingo.app.infrastructure.repository;

import com.bingo.app.infrastructure.entity.TenantRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRegistryRepository extends JpaRepository<TenantRegistry, Long> {

    Optional<TenantRegistry> findByAdminId(Long adminId);

    boolean existsByAdminId(Long adminId);
}
