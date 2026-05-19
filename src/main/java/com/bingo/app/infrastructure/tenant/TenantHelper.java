package com.bingo.app.infrastructure.tenant;

import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;

public class TenantHelper {

    public static void setFromUser(User user) {
        if (user == null) {
            TenantContext.set(TenantContext.masterTenant());
            return;
        }

        if (user.getRole() == Role.SUPER_ADMIN) {
            TenantContext.set(TenantContext.masterTenant());
        } else if (user.getRole() == Role.ADMIN) {
            TenantContext.set(TenantContext.agentTenant(user.getId()));
        } else if (user.getRole() == Role.PLAYER && user.getParentId() != null) {
            TenantContext.set(TenantContext.agentTenant(user.getParentId()));
        } else {
            TenantContext.set(TenantContext.masterTenant());
        }
    }

    public static void clear() {
        TenantContext.clear();
    }

    public static <T> T withTenant(User user, java.util.concurrent.Callable<T> callable) throws Exception {
        try {
            setFromUser(user);
            return callable.call();
        } finally {
            clear();
        }
    }

    public static void runWithTenant(User user, Runnable runnable) {
        try {
            setFromUser(user);
            runnable.run();
        } finally {
            clear();
        }
    }
}
