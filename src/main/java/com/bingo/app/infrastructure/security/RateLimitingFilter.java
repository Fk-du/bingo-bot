package com.bingo.app.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private final Cache<String, AtomicInteger> requestCounts;
    private final int maxRequests;
    private final int windowMs;

    public RateLimitingFilter(
            @Value("${bingo.rate-limit.max-requests:100}") int maxRequests,
            @Value("${bingo.rate-limit.window-ms:60000}") int windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(windowMs, TimeUnit.MILLISECONDS)
                .maximumSize(10000)
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String clientIp = resolveClientIp(request);
        String key = clientIp;

        AtomicInteger counter = requestCounts.get(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        if (count > maxRequests) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests\",\"userMessage\":\"Please slow down and try again later.\",\"status\":429}");
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
}
