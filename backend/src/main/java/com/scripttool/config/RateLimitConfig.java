package com.scripttool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 限流器配置 — 不同端点使用不同限流策略
 *
 * 令牌桶参数：
 *   capacity = 最大突发容量（桶装满时的令牌数）
 *   refillTokens = 每次补充的令牌数
 *   refillPeriod = 补充间隔
 *
 * 用户体验设计原则：
 *   - 正常操作：感受不到限流
 *   - 异常高频：返回 429 + 友好提示
 *   - AI接口：保守限流防止API费用失控
 */
@Configuration
public class RateLimitConfig {

    /**
     * 通用API限流：100次/分钟，突发上限200
     * 适用于：项目CRUD、文件夹操作、历史版本查询等
     */
    @Bean
    public RateLimiter generalLimiter() {
        return new RateLimiter(200, 100, Duration.ofMinutes(1));
    }

    /**
     * AI生成限流：3次/分钟，突发上限5
     * 适用于：AI剧本生成（成本高，耗时长）
     */
    @Bean
    public RateLimiter aiGenerateLimiter() {
        return new RateLimiter(5, 3, Duration.ofMinutes(1));
    }

    /**
     * AI对话限流：10次/分钟，突发上限20
     * 适用于：剧本AI对话编辑
     */
    @Bean
    public RateLimiter aiChatLimiter() {
        return new RateLimiter(20, 10, Duration.ofMinutes(1));
    }

    /**
     * 认证接口限流：20次/分钟，突发上限30（按IP）
     * 适用于：登录、注册（防止暴力破解）
     */
    @Bean
    public RateLimiter authLimiter() {
        return new RateLimiter(30, 20, Duration.ofMinutes(1));
    }

    /**
     * 文件上传限流：20次/分钟，突发上限30
     */
    @Bean
    public RateLimiter uploadLimiter() {
        return new RateLimiter(30, 20, Duration.ofMinutes(1));
    }

    /**
     * AI 生图限流：10次/分钟，突发上限15
     * 适用于：角色图/场景图生成（API调用+存储上传）
     */
    @Bean
    public RateLimiter aiImageLimiter() {
        return new RateLimiter(15, 10, Duration.ofMinutes(1));
    }

    /**
     * AI 搜索限流：20次/分钟，突发上限30
     * 适用于：知识库搜索
     */
    @Bean
    public RateLimiter aiSearchLimiter() {
        return new RateLimiter(30, 20, Duration.ofMinutes(1));
    }
}
