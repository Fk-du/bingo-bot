package com.bingo.app.infrastructure.tenant;

public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String MASTER_TENANT = "master";

    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String get() {
        return CURRENT_TENANT.get();
    }

    public static boolean isMaster() {
        return MASTER_TENANT.equals(CURRENT_TENANT.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static String masterTenant() {
        return MASTER_TENANT;
    }

    public static String agentTenant(Long adminId) {
        return "agent_" + adminId;
    }

    public static Long parseAdminId(String tenantId) {
        if (tenantId == null || !tenantId.startsWith("agent_")) {
            return null;
        }
        return Long.parseLong(tenantId.substring(6));
    }
}
