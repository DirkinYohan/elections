package com.elections.infrastructure.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Scripts Lua del lado del servidor. Redis ejecuta cada script completo sin intercalar otros comandos,
 * por eso son la forma de hacer operaciones compuestas indivisibles (RF-3). Restricción: un script largo
 * bloquea Redis entero mientras corre, así que todos son O(cambios), nunca O(total de datos).
 */
@Component
public class LuaScripts {

    private final StringRedisTemplate redis;

    private final RedisScript<List> submit = load(List.class, "lib.lua", "submit.lua");
    private final RedisScript<Long> flush = load(Long.class, "flush.lua");
    private final RedisScript<List> resume = load(List.class, "resume.lua");
    private final RedisScript<List> lockTake = load(List.class, "lock_take.lua");
    private final RedisScript<List> lockRenew = load(List.class, "lock_renew.lua");
    private final RedisScript<List> lockAdopt = load(List.class, "lock_adopt.lua");
    private final RedisScript<Long> lockRelease = load(Long.class, "lock_release.lua");
    private final RedisScript<List> resolve = load(List.class, "lib.lua", "resolve.lua");
    private final RedisScript<List> sweep = load(List.class, "sweep.lua");

    public LuaScripts(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<String> submit(String... args) { return strings(redis.execute(submit, Collections.emptyList(), (Object[]) args)); }
    public long flush(String... args) { Long r = redis.execute(flush, Collections.emptyList(), (Object[]) args); return r == null ? 0 : r; }
    public List<String> resume(String... args) { return strings(redis.execute(resume, Collections.emptyList(), (Object[]) args)); }
    public List<String> lockTake(String... args) { return strings(redis.execute(lockTake, Collections.emptyList(), (Object[]) args)); }
    public List<String> lockRenew(String... args) { return strings(redis.execute(lockRenew, Collections.emptyList(), (Object[]) args)); }
    public List<String> lockAdopt(String... args) { return strings(redis.execute(lockAdopt, Collections.emptyList(), (Object[]) args)); }
    public boolean lockRelease(String... args) { Long r = redis.execute(lockRelease, Collections.emptyList(), (Object[]) args); return r != null && r == 1; }
    public List<String> resolve(String... args) { return strings(redis.execute(resolve, Collections.emptyList(), (Object[]) args)); }
    public List<String> sweep() { return strings(redis.execute(sweep, Collections.emptyList())); }

    @SuppressWarnings("unchecked")
    private static List<String> strings(List<?> raw) {
        if (raw == null) return List.of();
        return raw.stream().map(o -> o == null ? null : String.valueOf(o)).toList();
    }

    private static <T> RedisScript<T> load(Class<T> type, String... files) {
        StringBuilder text = new StringBuilder();
        for (String file : files) {
            try (var in = new ClassPathResource("lua/" + file).getInputStream()) {
                text.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new DefaultRedisScript<>(text.toString(), type);
    }
}
