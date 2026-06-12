package com.bingo.app.infrastructure.persistence;

public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String MASTER = "master";

    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : MASTER;
    }

    public static boolean isMaster() {
        return MASTER.equals(getTenant());
    }

    public static String tenantKeyForAdmin(Long adminUserId) {
        return "agent_" + adminUserId;
    }

    public static Long parseAdminUserIdFromTenantKey(String tenantId) {
        if (tenantId == null || !tenantId.startsWith("agent_")) {
            return null;
        }
        try {
            return Long.parseLong(tenantId.substring(6));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
