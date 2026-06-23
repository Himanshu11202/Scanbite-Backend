package com.scanbite.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private final ConcurrentHashMap<String, RequestTracker> trackers = new ConcurrentHashMap<>();

    private static class RequestTracker {
        final long resetTime;
        final AtomicInteger count = new AtomicInteger(0);

        RequestTracker(long windowMs) {
            this.resetTime = System.currentTimeMillis() + windowMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > resetTime;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Target: auth routes (login, register) and file uploads
        boolean shouldLimit = path.equals("/api/auth/login") || 
                              path.equals("/api/auth/register") || 
                              path.contains("/image") || 
                              path.contains("/covers") || 
                              path.equals("/api/auth/profile/photo");

        if (shouldLimit) {
            String ip = getClientIP(request);
            String key = ip + ":" + path;
            
            trackers.compute(key, (k, tracker) -> {
                if (tracker == null || tracker.isExpired()) {
                    return new RequestTracker(60000); // 1 minute sliding window
                }
                return tracker;
            });
            
            RequestTracker tracker = trackers.get(key);
            if (tracker.count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
                System.err.println(String.format("[RATE LIMIT] Request blocked from IP: %s on path: %s", ip, path));
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Please try again in a minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
