package com.bingo.app.infrastructure.persistence;

import com.bingo.app.common.util.AdminIds;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantHelper {

    public static void setFromUser(User user) {
        if (user == null) {
            TenantContext.setTenant("master");
            return;
        }

        if (user.getRole() == Role.SUPER_ADMIN) {
            TenantContext.setTenant("master");
            log.debug("Set tenant to master for SUPER_ADMIN: {}", user.getId());
            return;
        }

        Long adminUserId = AdminIds.adminUserId(user);
        if (adminUserId != null) {
            String tenant = TenantContext.tenantKeyForAdmin(adminUserId);
            TenantContext.setTenant(tenant);
            log.debug("Set tenant to {} for user: {}", tenant, user.getId());
        } else {
            TenantContext.setTenant("master");
            log.debug("Set tenant to master for user: {} with role: {}", user.getId(), user.getRole());
        }
    }

    public static void clear() {
        TenantContext.clear();
    }

    public static <T> T withTenant(User user, java.util.concurrent.Callable<T> callable) {
        try {
            setFromUser(user);
            return callable.call();
        } catch (Exception e) {
            log.error("Error executing with tenant: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to execute with tenant context", e);
        } finally {
            clear();
        }
    }

    public static void runWithTenant(User user, Runnable runnable) {
        try {
            setFromUser(user);
            runnable.run();
        } catch (Exception e) {
            log.error("Error running with tenant: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to run with tenant context", e);
        } finally {
            clear();
        }
    }
}
