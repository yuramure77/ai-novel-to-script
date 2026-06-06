package com.scripttool.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitConfig rateLimitConfig;

    public WebMvcConfig(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA fallback — all non-API routes serve index.html
        registry.addViewController("/{spring:[^.]+}").setViewName("forward:/index.html");
        registry.addViewController("/projects/{spring:[^.]+}").setViewName("forward:/index.html");
        registry.addViewController("/project/{spring:[^.]+}").setViewName("forward:/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Auth: login/register — 20/min per IP (prevent brute force)
        registry.addInterceptor(new RateLimitInterceptor(
                rateLimitConfig.authLimiter(), "操作太频繁，请稍后再试"))
                .addPathPatterns("/api/auth/**");

        // AI generate — 3/min (expensive, CPU-intensive)
        registry.addInterceptor(new RateLimitInterceptor(
                rateLimitConfig.aiGenerateLimiter(), "AI 生成请求太频繁，请 20 秒后再试"))
                .addPathPatterns("/api/projects/*/generate/**");

        // AI chat — 10/min
        registry.addInterceptor(new RateLimitInterceptor(
                rateLimitConfig.aiChatLimiter(), "AI 对话请求太频繁，请稍后再试"))
                .addPathPatterns("/api/chat/**");

        // File upload — 20/min
        registry.addInterceptor(new RateLimitInterceptor(
                rateLimitConfig.uploadLimiter(), "文件上传太频繁，请稍后再试"))
                .addPathPatterns("/api/files/upload");

        // General API — 100/min (lowest priority, catch-all)
        registry.addInterceptor(new RateLimitInterceptor(
                rateLimitConfig.generalLimiter(), "请求太频繁，请稍后再试"))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**",
                        "/api/projects/*/generate/**",
                        "/api/chat/**",
                        "/api/files/upload",
                        "/api/deploy/webhook"); // Never rate-limit webhook
    }
}
