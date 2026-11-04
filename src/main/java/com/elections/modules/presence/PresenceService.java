package com.elections.modules.presence;

import com.elections.infrastructure.InstanceInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Presencia de auditores (RF-8), compartida por las ocho instancias.
 *
 *  - presence:<auditorId> HASH con nombre, instancia, conexión y último latido; expira solo (PEXPIRE).
 *  - presence:index       ZSET auditorId → último latido, para listar sin SCAN.
 *
 * Cada latido (cada 10 s) renueva el TTL de 25 s. Una conexión que se corta sin aviso (portátil sin
 * batería) deja de latir: su hash expira y el índice se poda por puntaje, así no quedan fantasmas.
 */
@Service
public class PresenceService {

    private static final String INDEX = "presence:index";
    private final StringRedisTemplate redis;
    private final InstanceInfo instance;
    private final long ttlMs;

    public PresenceService(StringRedisTemplate redis, InstanceInfo instance,
                           @Value("${app.verification.presence-ttl-ms:25000}") long ttlMs) {
        this.redis = redis;
        this.instance = instance;
        this.ttlMs = ttlMs;
    }

    public void touch(String auditorId, String name, long connectedAt) {
        long now = System.currentTimeMillis();
        String key = "presence:" + auditorId;
        redis.opsForHash().putIfAbsent(key, "connectedAt", String.valueOf(connectedAt));
        redis.opsForHash().putAll(key, Map.of("name", name, "lastSeen", String.valueOf(now), "instanceId", instance.id()));
        redis.expire(key, Duration.ofMillis(ttlMs));
        redis.opsForZSet().add(INDEX, auditorId, now);
    }

    public void remove(String auditorId) {
        redis.delete("presence:" + auditorId);
        redis.opsForZSet().remove(INDEX, auditorId);
    }

    public List<Map<String, Object>> list() {
        long now = System.currentTimeMillis();
        redis.opsForZSet().removeRangeByScore(INDEX, 0, now - ttlMs);
        Set<String> ids = redis.opsForZSet().range(INDEX, 0, -1);
        List<Map<String, Object>> out = new ArrayList<>();
        if (ids == null) return out;
        for (String id : ids) {
            Map<Object, Object> h = redis.opsForHash().entries("presence:" + id);
            if (h.isEmpty()) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("auditorId", id);
            p.put("name", h.get("name"));
            p.put("connectedAt", Long.parseLong(String.valueOf(h.getOrDefault("connectedAt", now))));
            p.put("lastSeen", Long.parseLong(String.valueOf(h.getOrDefault("lastSeen", now))));
            p.put("instanceId", h.get("instanceId"));
            String held = redis.opsForValue().get("holding:" + id);
            if (held != null) {
                Object since = redis.opsForHash().get("lock:" + held, "since");
                if (since != null) {
                    p.put("ballotId", held);
                    p.put("caseSince", Long.parseLong(String.valueOf(since)));
                }
            }
            out.add(p);
        }
        return out;
    }
}
