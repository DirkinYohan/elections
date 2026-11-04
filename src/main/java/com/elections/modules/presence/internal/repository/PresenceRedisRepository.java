package com.elections.modules.presence.internal.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

@Repository
public class PresenceRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String PRESENCE_KEY_PREFIX = "presence:auditor:";

    public PresenceRedisRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void updateHeartbeat(String auditorId, String status, Duration ttl) {
        stringRedisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + auditorId, status, ttl);
    }

    public void delete(String auditorId) {
        stringRedisTemplate.delete(PRESENCE_KEY_PREFIX + auditorId);
    }

    public Set<String> getActiveKeys() {
        return stringRedisTemplate.keys(PRESENCE_KEY_PREFIX + "*");
    }
}

