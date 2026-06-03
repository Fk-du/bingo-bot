package com.bingo.app.infrastructure.persistence;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getRole() == Role.SUPER_ADMIN) {
                TenantContext.setTenant("master");
            } else if (user.getRole() == Role.ADMIN) {
                TenantContext.setTenant("agent_" + user.getId());
            } else if (user.getRole() == Role.PLAYER && user.getAgentId() != null) {
                TenantContext.setTenant("agent_" + user.getAgentId());
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