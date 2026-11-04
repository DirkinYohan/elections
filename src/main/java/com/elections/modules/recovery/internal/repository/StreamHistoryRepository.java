package com.elections.modules.recovery.internal.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.atomic.AtomicLong;

@Repository
public class StreamHistoryRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final AtomicLong monotonicCounter = new AtomicLong(0);

    public StreamHistoryRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextMonotonicId() {
        return monotonicCounter.incrementAndGet();
    }
}

