package com.elections.modules.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Límite de tasa por credencial con ventana fija de un minuto, compartido por las ocho instancias.
 * INCR + EXPIRE en el primer uso: quien excede recibe 429 con los segundos que faltan para la próxima ventana.
 */
@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return 0 si se permite; si no, segundos a esperar. */
    public long check(String clientId, int limitPerMinute) {
        String key = "ratelimit:" + clientId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(1));
        if (count == null || count <= limitPerMinute) return 0;
        Long ttl = redis.getExpire(key);
        if (ttl == null || ttl < 0) {
            redis.expire(key, Duration.ofMinutes(1));
            ttl = 60L;
        }
        return Math.max(1, ttl);
    }
}
