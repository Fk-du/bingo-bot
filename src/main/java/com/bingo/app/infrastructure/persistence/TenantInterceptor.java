package com.bingo.app.infrastructure.persistence;

import com.bingo.app.common.util.AdminIds;
import com.bingo.app.master.entity.User;
import com.bingo.app.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            Object principal = auth.getPrincipal();
            User user = null;

            if (principal instanceof UserPrincipal userPrincipal) {
                user = userPrincipal.getUser();
            } else if (principal instanceof User rawUser) {
                user = rawUser;
            }

            if (user == null) {
                TenantContext.setTenant("master");
                return true;
            }

            Long adminUserId = AdminIds.adminUserId(user);
            if (adminUserId != null) {
                TenantContext.setTenant(TenantContext.tenantKeyForAdmin(adminUserId));
            } else {
                TenantContext.setTenant("master");
            }
        } else {
            TenantContext.setTenant("master");
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
