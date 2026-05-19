package com.bingo.app.infrastructure.security;

import com.bingo.app.infrastructure.tenant.TenantContext;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TelegramAuthenticationFilter extends OncePerRequestFilter {

    private final TelegramInitDataService telegramInitDataService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            User user = telegramInitDataService.resolveUser(authorizationHeader);
            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String tenantId = resolveTenant(user);
            TenantContext.set(tenantId);

            filterChain.doFilter(request, response);
        } catch (TelegramAuthException ex) {
            SecurityContextHolder.clearContext();
            restAuthenticationEntryPoint.commence(request, response, ex);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenant(User user) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            return TenantContext.masterTenant();
        }
        if (user.getRole() == Role.ADMIN) {
            return TenantContext.agentTenant(user.getId());
        }
        if (user.getRole() == Role.PLAYER && user.getParentId() != null) {
            return TenantContext.agentTenant(user.getParentId());
        }
        return TenantContext.masterTenant();
    }
}
