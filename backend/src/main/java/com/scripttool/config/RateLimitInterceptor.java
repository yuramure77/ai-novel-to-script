package com.scripttool.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.security.Principal;

/**
 * 限流拦截器 — 在请求到达 Controller 前检查令牌
 *
 * 限流优先级：
 *   1. 已登录用户 → 按 userId 限流
 *   2. 未登录用户 → 按 IP 限流
 *
 * 返回格式：JSON 统一错误格式匹配前端 ApiResponse
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final String message;

    public RateLimitInterceptor(RateLimiter limiter, String message) {
        this.limiter = limiter;
        this.message = message;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        String key = resolveKey(request);

        if (!limiter.tryConsume(key)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"code\":429,\"message\":\"" + message + "\",\"data\":null}"
            );
            return false;
        }
        return true;
    }

    private String resolveKey(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return "user:" + principal.getName();
        }
        // Fallback to IP
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // Only use first IP if chain
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return "ip:" + ip;
    }
}
