package com.elections.modules.juries;

import com.elections.infrastructure.Json;
import com.elections.infrastructure.metrics.MetricsService;
import com.elections.modules.identity.IdentityService;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket /ws/juries: solo servidor → jurado. Avisa cuando verificación resuelve un acta dudosa
 * del jurado. El aviso pasa por el canal Redis "jury" porque el jurado puede estar en otra instancia.
 * La confirmación del envío NO depende de este canal: viaja en la respuesta HTTP del POST.
 */
@Component
public class JurySocketHandler extends TextWebSocketHandler {

    private static final String CHANNEL = "jury";
    private final Map<String, Set<WebSocketSession>> byJury = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listener;
    private final MetricsService metrics;

    public JurySocketHandler(StringRedisTemplate redis, RedisMessageListenerContainer listener, MetricsService metrics) {
        this.redis = redis;
        this.listener = listener;
        this.metrics = metrics;
    }

    @PostConstruct
    void subscribe() {
        listener.addMessageListener((message, pattern) -> {
            Map<String, Object> e = Json.readMap(new String(message.getBody(), StandardCharsets.UTF_8));
            Set<WebSocketSession> sessions = byJury.get(String.valueOf(e.get("jurado")));
            if (sessions == null) return;
            String json = Json.write(e.get("message"));
            for (WebSocketSession s : sessions) {
                try {
                    if (s.isOpen()) s.sendMessage(new TextMessage(json));
                } catch (IOException | RuntimeException ignored) {
                    // jurado sin red: verá la decisión al consultar sus actas
                }
            }
        }, new ChannelTopic(CHANNEL));
    }

    public void notify(String jurado, String ballotId, int version, String decision, String notes) {
        Map<String, Object> msg = new java.util.LinkedHashMap<>();
        msg.put("type", "ballot_update");
        msg.put("ballotId", ballotId);
        msg.put("version", version);
        msg.put("decision", decision);
        if (notes != null) msg.put("notes", notes);
        msg.put("at", System.currentTimeMillis());
        redis.convertAndSend(CHANNEL, Json.write(Map.of("jurado", jurado, "message", msg)));
    }

    public int connections() {
        return byJury.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws IOException {
        IdentityService.Session s = (IdentityService.Session) raw.getAttributes().get("session");
        if (s == null || s.role() != IdentityService.Role.JURY) {
            raw.close(new CloseStatus(4401, "Unauthorized"));
            return;
        }
        raw.getAttributes().put("jurado", s.username());
        byJury.computeIfAbsent(s.username(), k -> ConcurrentHashMap.newKeySet()).add(new ConcurrentWebSocketSessionDecorator(raw, 10_000, 256 * 1024));
        metrics.wsConnects.incrementAndGet();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        Object jurado = raw.getAttributes().get("jurado");
        if (jurado == null) return;
        Set<WebSocketSession> set = byJury.get(String.valueOf(jurado));
        if (set != null) set.removeIf(s -> s.getId().equals(raw.getId()));
    }

    public int closeAll() {
        int n = 0;
        for (Set<WebSocketSession> set : byJury.values()) {
            for (WebSocketSession s : set) {
                try {
                    s.close(new CloseStatus(4000, "Instance going away"));
                    n++;
                } catch (IOException | RuntimeException ignored) {
                    // ya cerrada
                }
            }
        }
        return n;
    }
}
