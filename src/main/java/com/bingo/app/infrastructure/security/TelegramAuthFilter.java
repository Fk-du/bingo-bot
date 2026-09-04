package com.bingo.app.infrastructure.security;

import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.master.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramAuthFilter extends OncePerRequestFilter {

    private final TelegramAuthService telegramAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("tma ")) {
                String initData = authHeader.substring(4);

                try {
                    var user = telegramAuthService.authenticate(initData);

                if (user != null) {
                    // Enforce account status at the request boundary. This project
                    // authenticates via a stateless filter that bypasses
                    // DaoAuthenticationProvider, so UserDetails.isEnabled() is
                    // never consulted. We must reject inactive admins here, otherwise
                    // a suspended/disabled admin keeps ROLE_ADMIN and full access.
                    if (!UserPrincipal.isActiveAndEnabled(user)) {
                        log.warn("Blocked rejected/inactive user: {} (role={}, active={})",
                                user.getTelegramId(), user.getRole(), user.isActive());
                        writeForbidden(response, user);
                        return;
                    }

                    UserPrincipal principal = new UserPrincipal(user);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("User authenticated: {}", user.getTelegramId());
                } else {
                    log.warn("Authentication failed for token");
                }
            } catch (Exception e) {
                log.error("Authentication failed: {}", e.getMessage());
            }
        }

            chain.doFilter(request, response);
        } finally {
            TenantHelper.clear();
        }
    }

    private void writeForbidden(HttpServletResponse response, User user)
            throws IOException {
        String message = "Account is suspended";
        String userMessage = "Your account has been suspended. Contact the platform owner for details.";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(new ObjectMapper().writeValueAsString(
                Map.of(
                        "message", message,
                        "userMessage", userMessage,
                        "status", HttpServletResponse.SC_FORBIDDEN
                )));
    }
}
