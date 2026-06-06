package com.scripttool.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于令牌桶算法(Token Bucket)的内存限流服务
 * 按用户ID或IP地址分别计数，不影响正常用户体验
 */
public class RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int refillTokens;
    private final Duration refillPeriod;

    public RateLimiter(int capacity, int refillTokens, Duration refillPeriod) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriod = refillPeriod;
    }

    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());
        return bucket.tryConsume(1);
    }

    public long availableTokens(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());
        return bucket.getAvailableTokens();
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(refillTokens, refillPeriod));
        return Bucket.builder().addLimit(limit).build();
    }

    /** Cleanup stale entries periodically to prevent memory leak */
    public void cleanup() {
        // Simple cleanup: clear all. In production, use a scheduled task with TTL.
        if (buckets.size() > 10_000) {
            buckets.clear();
        }
    }
}
