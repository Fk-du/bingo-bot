package com.bingo.app.common.util;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;

public final class AdminIds {

    private AdminIds() {
    }

    /**
     * Admin operator id: {@code users.id} where role is ADMIN.
     * For players, this is the owning admin's user id (stored as admin_user_id in DB).
     */
    public static Long adminUserId(User user) {
        if (user == null) {
            return null;
        }
        return switch (user.getRole()) {
            case ADMIN -> user.getId();
            case PLAYER -> user.getAdminUserId();
            default -> null;
        };
    }
}
