package com.elections.interfaces.controller;

import com.elections.infrastructure.InstanceInfo;
import com.elections.infrastructure.chaos.ChaosService;
import com.elections.infrastructure.database.PersistenceService;
import com.elections.infrastructure.metrics.MetricsService;
import com.elections.modules.ballots.BallotService;
import com.elections.modules.broadcast.BroadcastService;
import com.elections.modules.catalog.CatalogService;
import com.elections.modules.identity.IdentityService;
import com.elections.modules.presence.PresenceService;
import com.elections.modules.results.ResultsService;
import com.elections.modules.security.RateLimitService;
import com.elections.modules.verification.VerificationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/** Única puerta HTTP del backend. Delega en los módulos; el contrato está en CONTRACT.md del frontend. */
@RestController
@RequestMapping("/api/v1")
public class ElectionsOrchestratorController {

    private final IdentityService identity;
    private final CatalogService catalog;
    private final BallotService ballots;
    private final RateLimitService rateLimit;
    private final ResultsService results;
    private final BroadcastService broadcast;
    private final VerificationService verification;
    private final PresenceService presence;
    private final MetricsService metrics;
    private final PersistenceService persistence;
    private final ChaosService chaos;
    private final InstanceInfo instance;
    private final int rateLimitPerMinute;

    public ElectionsOrchestratorController(IdentityService identity, CatalogService catalog, BallotService ballots,
                                           RateLimitService rateLimit, ResultsService results, BroadcastService broadcast,
                                           VerificationService verification, PresenceService presence, MetricsService metrics,
                                           PersistenceService persistence, ChaosService chaos, InstanceInfo instance,
                                           @Value("${app.ballots.rate-limit-per-minute:120}") int rateLimitPerMinute) {
        this.identity = identity;
        this.catalog = catalog;
        this.ballots = ballots;
        this.rateLimit = rateLimit;
        this.results = results;
        this.broadcast = broadcast;
        this.verification = verification;
        this.presence = presence;
        this.metrics = metrics;
        this.persistence = persistence;
        this.chaos = chaos;
        this.instance = instance;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("status", "UP");
        h.put("instanceId", instance.id());
        String redisStatus;
        try {
            results.ranking("department");
            redisStatus = chaos.redisDown() ? "SIMULATED_DOWN" : "UP";
        } catch (RuntimeException e) {
            redisStatus = "DOWN";
        }
        h.put("redis", redisStatus);
        h.put("postgres", persistence.ready() ? "UP" : "UNAVAILABLE");
        return h;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            return identity.authenticate(body.get("username"), body.get("password"))
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "Invalid credentials")));
        } catch (DataAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error", "Sistema degradado: Redis no responde"));
        }
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> catalog() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePublic()).body(catalog.catalog());
    }

    // ---- Jurados (RF-1, RF-2) -------------------------------------------------------------------

    @PostMapping("/ballots")
    public ResponseEntity<?> submit(@RequestBody(required = false) Map<String, Object> sheet,
                                    @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous-jury") String clientId,
                                    HttpServletResponse servlet) {
        try {
            long wait = rateLimit.check(clientId, rateLimitPerMinute);
            if (wait > 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(wait))
                        .body(Map.of("error", "Rate limit exceeded", "retryAfterSeconds", wait));
            }
        } catch (DataAccessException e) {
            // sin Redis tampoco se puede registrar: lo responde el servicio con 503
        }
        BallotService.Outcome out = ballots.submit(sheet);
        if (out.httpStatus() < 300 && chaos.dropResponseRate() > 0 && Math.random() < chaos.dropResponseRate()) {
            // Demostración RF-1: el acta YA quedó registrada, pero el "servidor muere" antes de confirmar.
            // El cliente la mantiene en cola y la reenvía con el mismo idem: recibirá DUPLICATE con el mismo recibo.
            servlet.setHeader("Connection", "close");
            return ResponseEntity.status(502).body(Map.of("error", "Conexión perdida antes de confirmar"));
        }
        return ResponseEntity.status(out.httpStatus()).body(out.body());
    }

    // ---- Público (RF-4, RF-5, RF-6) ------------------------------------------------------------

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public ResponseEntity<?> stream(@RequestParam(defaultValue = "department") String topic,
                                    @RequestParam(required = false) String lastEventId,
                                    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
                                    HttpServletResponse servlet) {
        if (!catalog.isTopic(topic)) return ResponseEntity.badRequest().body(Map.of("error", "Tópico inválido"));
        servlet.setHeader("Cache-Control", "no-cache, no-transform");
        servlet.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = broadcast.open(topic, lastEventId != null ? lastEventId : lastEventIdHeader);
        return ResponseEntity.ok(emitter);
    }

    @GetMapping("/results/department")
    public Map<String, Object> department() {
        return results.aggregate("DEPARTMENT", "GLOBAL", "department");
    }

    @GetMapping("/results/municipality/{id}")
    public Map<String, Object> municipality(@PathVariable int id) {
        return results.aggregate("MUNICIPALITY", String.valueOf(id), "municipality:" + id);
    }

    @GetMapping("/results/station/{id}")
    public Map<String, Object> station(@PathVariable int id) {
        return results.aggregate("POLLING_STATION", String.valueOf(id), "station:" + id);
    }

    // ---- Verificación y operación (lectura REST) -------------------------------------------------

    @GetMapping("/verification/queue")
    public Object queue() {
        return verification.queue();
    }

    @GetMapping("/verification/auditors")
    public ResponseEntity<?> auditors(@RequestHeader(value = "Authorization", required = false) String auth) {
        var s = IdentityService.bearer(auth).flatMap(identity::session);
        if (s.isEmpty() || s.get().role() == IdentityService.Role.JURY) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(presence.list());
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.globalView();
    }

    @GetMapping("/audit/{ballotId}")
    public Object audit(@PathVariable String ballotId) {
        return persistence.auditTrail(ballotId);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> degraded(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("status", "UNAVAILABLE", "error", "Sistema degradado: Redis no responde"));
    }
}
