package com.bingo.app.infrastructure.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.multi-tenant.enabled", havingValue = "true", matchIfMissing = true)
public class TenantInitializer implements ApplicationRunner {

    private final TenantManagementService tenantManagementService;

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.set(TenantContext.masterTenant());
        try {
            tenantManagementService.ensureMasterSchema();
            tenantManagementService.initializeTenants();
            log.info("Tenant initialization complete.");
        } catch (Exception e) {
            log.error("Tenant initialization failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            TenantContext.clear();
        }
    }
}
