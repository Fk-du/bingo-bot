package com.bingo.app.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lightweight per-user (fallback: per-IP) rate limiter applied to high-abuse
 * endpoints. Sensitive actions such as claiming Bingo and (re)registering are
 * throttled more strictly than general API traffic.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CLAIM = "claim";
    private static final String REGISTER = "register";
    private static final String GENERAL = "general";

    private final int generalMax;
    private final long generalWindowMs;
    private final int claimMax;
    private final int registerMax;

    private final Map<String, ConcurrentLinkedQueue<Long>> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCleanup = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${bingo.rate-limit.max-requests:100}") int generalMax,
            @Value("${bingo.rate-limit.window-ms:60000}") long generalWindowMs,
            @Value("${bingo.rate-limit.claim-max:5}") int claimMax,
            @Value("${bingo.rate-limit.register-max:10}") int registerMax) {
        this.generalMax = generalMax;
        this.generalWindowMs = generalWindowMs;
        this.claimMax = claimMax;
        this.registerMax = registerMax;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        return !uri.matches(".*/games/[^/]+/(claim|register)$");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = resolveKey(request);
        String limitName = resolveLimit(request);

        int max = switch (limitName) {
            case CLAIM -> this.claimMax;
            case REGISTER -> this.registerMax;
            default -> this.generalMax;
        };
        long windowMs = limitName.equals(GENERAL) ? this.generalWindowMs : this.generalWindowMs;

        String bucketKey = key + ":" + limitName;
        long now = System.currentTimeMillis();
        ConcurrentLinkedQueue<Long> timestamps = this.buckets.computeIfAbsent(bucketKey, k -> new ConcurrentLinkedQueue<>());

        // Opportunistic cleanup whenever a bucket is touched.
        Long last = this.lastCleanup.get(key);
        if (last == null || now - last > windowMs) {
            cleanupBucket(timestamps, now, windowMs);
            this.lastCleanup.put(key, now);
        }

        timestamps.add(now);

        if (timestamps.size() > max) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Too many requests. Please slow down and try again shortly.\"}");
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapped);
        wrapped.copyBodyToResponse();
    }

    private void cleanupBucket(ConcurrentLinkedQueue<Long> timestamps, long now, long windowMs) {
        Long earliest;
        while ((earliest = timestamps.peek()) != null && now - earliest > windowMs) {
            timestamps.poll();
        }
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return "u" + principal.getUser().getId();
        }
        return "ip" + remoteAddr(request);
    }

    private String resolveLimit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.matches(".*/games/[^/]+/claim$")) {
            return CLAIM;
        }
        if (path.matches(".*/games/[^/]+/register$")) {
            return REGISTER;
        }
        return GENERAL;
    }

    private String remoteAddr(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
