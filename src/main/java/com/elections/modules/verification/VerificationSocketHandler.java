package com.elections.modules.verification;

import com.elections.infrastructure.InstanceInfo;
import com.elections.infrastructure.Json;
import com.elections.infrastructure.database.PersistenceService;
import com.elections.infrastructure.metrics.MetricsService;
import com.elections.modules.identity.IdentityService;
import com.elections.modules.juries.JurySocketHandler;
import com.elections.modules.presence.PresenceService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket /ws/verification de auditores y coordinador (RF-7, RF-8). Las consolas pueden estar en
 * instancias distintas: todo cambio de la cola se publica en el canal Redis "verif" y cada instancia
 * lo reenvía a sus consolas. El lockToken solo viaja a la conexión dueña del bloqueo.
 */
@Component
public class VerificationSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VerificationSocketHandler.class);

    static final class Console {
        final String connId;
        final String auditorId;
        final String name;
        final IdentityService.Role role;
        final WebSocketSession ws;
        final long connectedAt = System.currentTimeMillis();
        volatile long lastSeen = connectedAt;

        Console(String connId, IdentityService.Session s, WebSocketSession ws) {
            this.connId = connId;
            this.auditorId = s.username();
            this.name = s.username();
            this.role = s.role();
            this.ws = ws;
        }
    }

    private final Map<String, Console> consoles = new ConcurrentHashMap<>();
    private final VerificationService verification;
    private final PresenceService presence;
    private final JurySocketHandler juries;
    private final PersistenceService persistence;
    private final MetricsService metrics;
    private final InstanceInfo instance;
    private final RedisMessageListenerContainer listener;
    private final long heartbeatMs;
    private final long presenceTtlMs;
    private final AtomicBoolean presenceDirty = new AtomicBoolean();

    public VerificationSocketHandler(VerificationService verification, PresenceService presence, JurySocketHandler juries,
                                     PersistenceService persistence, MetricsService metrics, InstanceInfo instance,
                                     RedisMessageListenerContainer listener,
                                     @Value("${app.verification.heartbeat-ms:10000}") long heartbeatMs,
                                     @Value("${app.verification.presence-ttl-ms:25000}") long presenceTtlMs) {
        this.verification = verification;
        this.presence = presence;
        this.juries = juries;
        this.persistence = persistence;
        this.metrics = metrics;
        this.instance = instance;
        this.listener = listener;
        this.heartbeatMs = heartbeatMs;
        this.presenceTtlMs = presenceTtlMs;
    }

    @PostConstruct
    void subscribe() {
        listener.addMessageListener((message, pattern) -> onBus(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(VerificationService.CHANNEL));
    }

    public int connections() {
        return consoles.size();
    }

    // ---- Ciclo de vida de la conexión --------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws IOException {
        IdentityService.Session s = (IdentityService.Session) raw.getAttributes().get("session");
        if (s == null || (s.role() != IdentityService.Role.AUDITOR && s.role() != IdentityService.Role.COORDINATOR)) {
            raw.close(new CloseStatus(4401, "Unauthorized"));
            return;
        }
        WebSocketSession ws = new ConcurrentWebSocketSessionDecorator(raw, 10_000, 1024 * 1024);
        Console me = new Console(instance.nextConnectionId(), s, ws);
        consoles.put(raw.getId(), me);
        metrics.wsConnects.incrementAndGet();

        send(me, Map.of("type", "welcome", "auditorId", me.auditorId, "name", me.name, "role", me.role.name(),
                "instanceId", instance.id(), "lockTtlMs", verification.lockTtlMs(), "heartbeatMs", heartbeatMs));
        try {
            send(me, Map.of("type", "queue_snapshot", "cases", verification.queue()));
            if (me.role == IdentityService.Role.AUDITOR) {
                presence.touch(me.auditorId, me.name, me.connectedAt);
                // ¿Reconectó y sigue siendo dueño de un caso? Esta conexión pasa a ser la dueña y recibe el token.
                List<String> adopted = verification.adopt(me.auditorId, me.connId);
                if (!adopted.isEmpty()) publishLocked(adopted, me);
                verification.publish(Map.of("type", "presence_changed"));
            }
            sendPresence(me);
        } catch (RuntimeException e) {
            send(me, Map.of("type", "error", "code", "BAD_MESSAGE", "message", "Sistema degradado: Redis no responde"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        Console me = consoles.remove(raw.getId());
        if (me == null || me.role != IdentityService.Role.AUDITOR) return;
        // El bloqueo NO se libera aquí: vence solo si nadie lo renueva con latidos activos.
        boolean otherLocal = consoles.values().stream().anyMatch(c -> c.auditorId.equals(me.auditorId));
        try {
            if (!otherLocal) presence.remove(me.auditorId);
            verification.publish(Map.of("type", "presence_changed"));
        } catch (RuntimeException ignored) {
            // la presencia expira sola por TTL
        }
    }

    // ---- Mensajes del cliente ----------------------------------------------------------------

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
        Console me = consoles.get(raw.getId());
        if (me == null) return;
        me.lastSeen = System.currentTimeMillis();
        Map<String, Object> msg;
        try {
            msg = Json.readMap(message.getPayload());
        } catch (RuntimeException e) {
            send(me, error("BAD_MESSAGE", "JSON inválido"));
            return;
        }
        try {
            handle(me, msg);
        } catch (RuntimeException e) {
            log.warn("Error en verificación: {}", e.getMessage());
            send(me, error("BAD_MESSAGE", "Sistema degradado: no se pudo procesar la acción"));
        }
    }

    private void handle(Console me, Map<String, Object> msg) {
        String type = String.valueOf(msg.get("type"));
        if ("heartbeat".equals(type)) {
            if (me.role != IdentityService.Role.AUDITOR) return;
            presence.touch(me.auditorId, me.name, me.connectedAt);
            if (Boolean.TRUE.equals(msg.get("active"))) {
                List<String> renewed = verification.renew(me.auditorId, me.connId);
                if (!renewed.isEmpty()) publishLocked(renewed, me);
            }
            return;
        }
        if (me.role != IdentityService.Role.AUDITOR) {
            send(me, error("READ_ONLY", "El coordinador solo observa"));
            return;
        }
        String ballotId = String.valueOf(msg.getOrDefault("ballotId", ""));
        String token = msg.get("lockToken") == null ? null : String.valueOf(msg.get("lockToken"));
        switch (type) {
            case "take_case" -> {
                List<String> r = verification.take(ballotId, me.auditorId, me.name, UUID.randomUUID().toString(), me.connId);
                switch (r.get(0)) {
                    case "OK" -> {
                        List<String> lock = verification.adopt(me.auditorId, me.connId);
                        if (!lock.isEmpty()) publishLocked(lock, me);
                        verification.publish(Map.of("type", "presence_changed"));
                        persistence.audit("CASE_LOCKED", ballotId, me.auditorId, "Caso tomado para revisión");
                    }
                    case "TAKEN" -> send(me, Map.of("type", "take_rejected", "ballotId", ballotId, "lockedBy", r.get(1)));
                    case "ALREADY" -> send(me, error("ALREADY_HOLDING_CASE", "Ya estás trabajando en " + r.get(1)));
                    default -> send(me, error("CASE_NOT_FOUND", "El caso ya no existe"));
                }
            }
            case "release_case" -> {
                if (!verification.release(ballotId, token)) {
                    send(me, error("LOCK_NOT_OWNED", "Tu bloqueo expiró o el caso ya pertenece a otro auditor"));
                    return;
                }
                verification.publish(Map.of("type", "case_released", "ballotId", ballotId, "reason", "released"));
                verification.publish(Map.of("type", "presence_changed"));
                persistence.audit("CASE_RELEASED", ballotId, me.auditorId, "Caso devuelto a la cola");
            }
            case "resolve_case" -> {
                String decision = String.valueOf(msg.get("decision"));
                if (!List.of("RATIFIED", "CORRECTED", "REJECTED").contains(decision)) {
                    send(me, error("BAD_MESSAGE", "Decisión inválida"));
                    return;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> corrected = msg.get("correctedVotes") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
                if ("CORRECTED".equals(decision) && corrected == null) {
                    send(me, error("BAD_MESSAGE", "CORRECTED necesita correctedVotes"));
                    return;
                }
                List<String> r = verification.resolve(ballotId, token, decision, corrected);
                if (!"OK".equals(r.get(0))) {
                    send(me, "CASE_NOT_FOUND".equals(r.get(0)) ? error("CASE_NOT_FOUND", "El caso ya no existe")
                            : error("LOCK_NOT_OWNED", "Tu bloqueo expiró o el caso ya pertenece a otro auditor"));
                    return;
                }
                Map<String, Object> item = Json.readMap(r.get(1));
                String notes = msg.get("notes") == null ? null : String.valueOf(msg.get("notes"));
                verification.publish(Map.of("type", "case_resolved", "ballotId", ballotId, "auditorId", me.auditorId, "decision", decision));
                verification.publish(Map.of("type", "presence_changed"));
                juries.notify(String.valueOf(item.get("jurado")), ballotId, ((Number) item.get("version")).intValue(), decision, notes);
                persistence.audit("CASE_RESOLVED", ballotId, me.auditorId, "Decisión: " + decision + (notes == null ? "" : " · " + notes));
            }
            default -> send(me, error("BAD_MESSAGE", "Tipo de mensaje desconocido"));
        }
    }

    /** lock = {ballotId, token, name, since, expiresAt} de lock_adopt / lock_renew. */
    private void publishLocked(List<String> lock, Console owner) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", "case_locked");
        e.put("ballotId", lock.get(0));
        e.put("auditorId", owner.auditorId);
        e.put("auditorName", lock.get(2));
        e.put("lockExpiresAt", Long.parseLong(lock.get(4)));
        e.put("since", Long.parseLong(lock.get(3)));
        e.put("lockToken", lock.get(1));
        e.put("connId", owner.connId);
        verification.publish(e);
    }

    // ---- Eventos del bus (todas las instancias) -----------------------------------------------

    private void onBus(String json) {
        Map<String, Object> e;
        try {
            e = Json.readMap(json);
        } catch (RuntimeException ex) {
            return;
        }
        String type = String.valueOf(e.get("type"));
        switch (type) {
            case "presence_changed" -> presenceDirty.set(true);
            case "case_added" -> verification.caseView(String.valueOf(e.get("ballotId")))
                    .ifPresent(item -> broadcast(Map.of("type", "case_added", "item", item)));
            case "case_locked" -> {
                String ownerConn = String.valueOf(e.remove("connId"));
                Object token = e.remove("lockToken");
                String plain = Json.write(e);
                e.put("lockToken", token);
                String withToken = Json.write(e);
                for (Console c : consoles.values()) sendRaw(c, c.connId.equals(ownerConn) ? withToken : plain);
            }
            default -> broadcast(e);
        }
    }

    private void broadcast(Map<String, Object> msg) {
        String json = Json.write(msg);
        for (Console c : consoles.values()) sendRaw(c, json);
    }

    // ---- Tareas periódicas ---------------------------------------------------------------------

    /** Casos cuyo bloqueo venció solo: vuelven a la cola en todas las consolas. Consolas sin latido: se cierran. */
    @Scheduled(fixedDelay = 1000)
    void sweep() {
        long now = System.currentTimeMillis();
        for (Console c : consoles.values()) {
            if (now - c.lastSeen > presenceTtlMs) {
                try {
                    c.ws.close(new CloseStatus(4000, "No heartbeat"));
                } catch (IOException | RuntimeException ignored) {
                    // ya cerrada
                }
            }
        }
        try {
            for (String ballotId : verification.sweepExpired()) {
                verification.publish(Map.of("type", "case_released", "ballotId", ballotId, "reason", "expired"));
                verification.publish(Map.of("type", "presence_changed"));
            }
        } catch (RuntimeException ignored) {
            // Redis caído
        }
        if (presenceDirty.getAndSet(false)) broadcastPresence();
    }

    @Scheduled(fixedRate = 5000)
    void periodicPresence() {
        if (!consoles.isEmpty()) broadcastPresence();
    }

    private void broadcastPresence() {
        try {
            String json = Json.write(Map.of("type", "presence", "auditors", presence.list()));
            for (Console c : consoles.values()) sendRaw(c, json);
        } catch (RuntimeException ignored) {
            // Redis caído
        }
    }

    private void sendPresence(Console c) {
        sendRaw(c, Json.write(Map.of("type", "presence", "auditors", presence.list())));
    }

    /** Simulación de la muerte de esta instancia (RF-10). */
    public int closeAll() {
        int n = consoles.size();
        for (Console c : consoles.values()) {
            try {
                c.ws.close(new CloseStatus(4000, "Instance going away"));
            } catch (IOException | RuntimeException ignored) {
                // ya cerrada
            }
        }
        return n;
    }

    private static Map<String, Object> error(String code, String message) {
        return Map.of("type", "error", "code", code, "message", message);
    }

    private void send(Console c, Map<String, Object> msg) {
        sendRaw(c, Json.write(msg));
    }

    private void sendRaw(Console c, String json) {
        try {
            if (c.ws.isOpen()) c.ws.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            // consola lenta o caída: el decorador la cierra si excede el búfer
        }
    }
}
