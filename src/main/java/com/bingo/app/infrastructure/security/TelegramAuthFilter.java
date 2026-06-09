package com.bingo.app.infrastructure.security;

import com.bingo.app.infrastructure.persistence.TenantHelper;
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
                        UserPrincipal principal = new UserPrincipal(user);
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        TenantHelper.setFromUser(user);
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
}