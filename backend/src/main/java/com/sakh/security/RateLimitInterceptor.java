package com.sakh.security;

import com.sakh.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Enforces per-client rate limits on authentication and chat endpoints.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    private final int loginLimit;
    private final int registerLimit;
    private final int chatLimit;
    private final int windowSeconds;

    public RateLimitInterceptor(RateLimitService rateLimitService,
                                @Value("${app.rate-limit.login-limit:10}") int loginLimit,
                                @Value("${app.rate-limit.register-limit:5}") int registerLimit,
                                @Value("${app.rate-limit.chat-limit:60}") int chatLimit,
                                @Value("${app.rate-limit.window-seconds:60}") int windowSeconds) {
        this.rateLimitService = rateLimitService;
        this.loginLimit = loginLimit;
        this.registerLimit = registerLimit;
        this.chatLimit = chatLimit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        Duration window = Duration.ofSeconds(windowSeconds);

        if (path.endsWith("/auth/login")) {
            String key = "login:" + clientIp(request);
            requireAcquire(key, loginLimit, window, "Too many login attempts. Please try again later.");
        } else if (path.endsWith("/auth/register")) {
            String key = "register:" + clientIp(request);
            requireAcquire(key, registerLimit, window, "Too many registration attempts. Please try again later.");
        } else if (path.endsWith("/chat") || path.endsWith("/chat/stream")) {
            String email = currentUserEmail();
            if (email != null) {
                requireAcquire("chat:" + email, chatLimit, window,
                        "Too many chat requests. Please wait a moment and try again.");
            }
        }

        return true;
    }

    private void requireAcquire(String key, int limit, Duration window, String message) {
        if (!rateLimitService.tryAcquire(key, limit, window)) {
            throw new RateLimitExceededException(message);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String currentUserEmail() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return null;
    }
}