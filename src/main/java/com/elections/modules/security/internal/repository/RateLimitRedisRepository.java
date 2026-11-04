package com.elections.modules.security.internal.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RateLimitRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String RATE_LIMIT_PREFIX = "ratelimit:";

    public RateLimitRedisRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long incrementCounter(String clientId, Duration window) {
        String key = RATE_LIMIT_PREFIX + clientId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, window);
        }
        return count;
    }
}

