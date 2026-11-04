package com.elections.modules.verification;

import com.elections.infrastructure.Json;
import com.elections.infrastructure.redis.LuaScripts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Cola de verificación con asignación exclusiva (RF-7).
 *
 *  - cases        HASH  ballotId → caso (JSON)
 *  - cases:queue  ZSET  ballotId → prioridad (la consola los ordena de mayor a menor)
 *  - lock:<id>    HASH  token, auditorId, auditorName, connId, since — con PEXPIRE = LOCK_TTL_MS
 *  - holding:<a>  STRING el caso que tiene el auditor (un auditor, un caso a la vez)
 *  - locks:active SET   casos bloqueados, para detectar los que expiraron solos y avisar a las consolas
 *
 * La tensión del requisito: el bloqueo dura poco (30 s) y SOLO lo renuevan los latidos activos de la
 * conexión dueña. Si el auditor cierra el navegador, pierde la red o se va a almorzar, nadie lo renueva
 * y el caso vuelve solo a la cola; si sigue trabajando, cada latido activo (10 s) lo extiende.
 */
@Service
public class VerificationService {

    public static final String CHANNEL = "verif";
    private final StringRedisTemplate redis;
    private final LuaScripts lua;
    private final long lockTtlMs;

    public VerificationService(StringRedisTemplate redis, LuaScripts lua, @Value("${app.verification.lock-ttl-ms:30000}") long lockTtlMs) {
        this.redis = redis;
        this.lua = lua;
        this.lockTtlMs = lockTtlMs;
    }

    public long lockTtlMs() {
        return lockTtlMs;
    }

    public List<String> take(String ballotId, String auditorId, String name, String token, String connId) {
        return lua.lockTake(ballotId, auditorId, name, token, connId, String.valueOf(lockTtlMs), String.valueOf(System.currentTimeMillis()));
    }

    public List<String> renew(String auditorId, String connId) {
        return lua.lockRenew(auditorId, connId, String.valueOf(lockTtlMs), String.valueOf(System.currentTimeMillis()));
    }

    public List<String> adopt(String auditorId, String connId) {
        return lua.lockAdopt(auditorId, connId, String.valueOf(System.currentTimeMillis()));
    }

    public boolean release(String ballotId, String token) {
        return lua.lockRelease(ballotId, token == null ? "" : token);
    }

    public List<String> resolve(String ballotId, String token, String decision, Map<String, Object> correctedVotes) {
        String corrected = correctedVotes == null || correctedVotes.isEmpty() ? "" : Json.write(correctedVotes);
        return lua.resolve(ballotId, token == null ? "" : token, decision, corrected, String.valueOf(System.currentTimeMillis()));
    }

    public List<String> sweepExpired() {
        return lua.sweep();
    }

    public void publish(Map<String, Object> event) {
        redis.convertAndSend(CHANNEL, Json.write(event));
    }

    public int queueSize() {
        Long n = redis.opsForZSet().zCard("cases:queue");
        return n == null ? 0 : n.intValue();
    }

    /** Caso tal como lo ve la consola: sin el jurado y con el bloqueo vigente, si lo hay. */
    public Optional<Map<String, Object>> caseView(String ballotId) {
        List<Map<String, Object>> list = views(List.of(ballotId));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<Map<String, Object>> queue() {
        Set<String> ids = redis.opsForZSet().reverseRange("cases:queue", 0, -1);
        return ids == null || ids.isEmpty() ? List.of() : views(new ArrayList<>(ids));
    }

    /** Una sola ida y vuelta a Redis por lote (pipeline), aunque la cola tenga cientos de casos. */
    private List<Map<String, Object>> views(List<String> ids) {
        List<Object> raw = redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations ops) {
                for (String id : ids) {
                    ops.opsForHash().get("cases", id);
                    ops.opsForHash().multiGet("lock:" + id, List.of("auditorId", "auditorName"));
                    ops.getExpire("lock:" + id, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
                return null;
            }
        });
        List<Map<String, Object>> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < ids.size(); i++) {
            Object json = raw.get(i * 3);
            if (json == null) continue;
            Map<String, Object> item = new LinkedHashMap<>(Json.readMap(String.valueOf(json)));
            item.remove("jurado");
            @SuppressWarnings("unchecked")
            List<Object> lock = (List<Object>) raw.get(i * 3 + 1);
            Object pttl = raw.get(i * 3 + 2);
            long ttl = pttl instanceof Number n ? n.longValue() : -1;
            if (lock != null && lock.get(0) != null && ttl > 0) {
                item.put("lockedBy", lock.get(0));
                item.put("lockedByName", lock.get(1));
                item.put("lockExpiresAt", now + ttl);
            }
            out.add(item);
        }
        return out;
    }
}
