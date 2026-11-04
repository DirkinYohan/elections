package com.elections.infrastructure.chaos;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Fallas simuladas para la sustentación (RF-1, RF-10). Viven en Redis para que las ocho instancias
 * se comporten igual; cada instancia las copia en memoria una vez por segundo.
 */
@Service
public class ChaosService {

    private static final String KEY = "chaos";
    private final StringRedisTemplate redis;
    private volatile boolean redisDown;
    private volatile double dropResponseRate;

    public ChaosService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean redisDown() {
        return redisDown;
    }

    public double dropResponseRate() {
        return dropResponseRate;
    }

    public void setRedisDown(boolean down) {
        redis.opsForHash().put(KEY, "redisDown", String.valueOf(down));
        redisDown = down;
    }

    public void setDropResponseRate(double rate) {
        double clamped = Math.max(0, Math.min(1, rate));
        redis.opsForHash().put(KEY, "dropResponseRate", String.valueOf(clamped));
        dropResponseRate = clamped;
    }

    @Scheduled(fixedDelay = 1000)
    void refresh() {
        try {
            Map<Object, Object> m = redis.opsForHash().entries(KEY);
            redisDown = Boolean.parseBoolean(String.valueOf(m.getOrDefault("redisDown", "false")));
            dropResponseRate = Double.parseDouble(String.valueOf(m.getOrDefault("dropResponseRate", "0")));
        } catch (RuntimeException ignored) {
            // Redis caído de verdad: se conserva el último valor conocido.
        }
    }
}
