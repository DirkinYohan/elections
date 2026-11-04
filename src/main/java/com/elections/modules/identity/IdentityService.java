package com.elections.modules.identity;

import com.elections.infrastructure.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identidad. Las sesiones viven en Redis (no en memoria del proceso): sin sesiones pegajosas (RT-2),
 * la siguiente petición del mismo usuario puede caer en cualquiera de las ocho instancias.
 *
 * Cuentas de demostración: jury1…jury6400, auditor1…auditor120 y coordinator1, con la contraseña de DEMO_PASSWORD.
 */
@Service
public class IdentityService {

    public enum Role { JURY, AUDITOR, COORDINATOR, PUBLIC }

    public record Session(String token, String username, Role role, long expiresAt) {}

    private static final Pattern USER = Pattern.compile("^(jury|auditor|coordinator)(\\d+)$");
    private static final Duration TTL = Duration.ofHours(8);
    private final StringRedisTemplate redis;
    private final String password;

    public IdentityService(StringRedisTemplate redis, @Value("${app.demo.password:pass123}") String password) {
        this.redis = redis;
        this.password = password;
    }

    public Optional<Session> authenticate(String username, String pass) {
        if (username == null || pass == null || !password.equals(pass)) return Optional.empty();
        Optional<Role> role = roleOf(username.trim());
        if (role.isEmpty()) return Optional.empty();
        Session s = new Session(UUID.randomUUID().toString(), username.trim(), role.get(), System.currentTimeMillis() + TTL.toMillis());
        redis.opsForValue().set("session:" + s.token(), Json.write(s), TTL);
        return Optional.of(s);
    }

    public Optional<Session> session(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String raw = redis.opsForValue().get("session:" + token);
        if (raw == null) return Optional.empty();
        Session s = Json.read(raw, Session.class);
        return s.expiresAt() > System.currentTimeMillis() ? Optional.of(s) : Optional.empty();
    }

    public static Optional<String> bearer(String header) {
        if (header == null) return Optional.empty();
        String t = header.replaceFirst("(?i)^Bearer\\s+", "").trim();
        return t.isEmpty() ? Optional.empty() : Optional.of(t);
    }

    private static Optional<Role> roleOf(String username) {
        Matcher m = USER.matcher(username);
        if (!m.matches()) return Optional.empty();
        int n = Integer.parseInt(m.group(2));
        return switch (m.group(1)) {
            case "jury" -> n >= 1 && n <= 6400 ? Optional.of(Role.JURY) : Optional.empty();
            case "auditor" -> n >= 1 && n <= 120 ? Optional.of(Role.AUDITOR) : Optional.empty();
            default -> n >= 1 && n <= 5 ? Optional.of(Role.COORDINATOR) : Optional.empty();
        };
    }
}
