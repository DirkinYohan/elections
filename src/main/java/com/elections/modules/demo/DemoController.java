package com.elections.modules.demo;

import com.elections.infrastructure.InstanceInfo;
import com.elections.infrastructure.chaos.ChaosService;
import com.elections.modules.broadcast.BroadcastService;
import com.elections.modules.catalog.CatalogService;
import com.elections.modules.juries.JurySocketHandler;
import com.elections.modules.verification.VerificationService;
import com.elections.modules.verification.VerificationSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controles de la sustentación (/api/v1/mock/*), los mismos que usa el tablero de operación.
 * Se desactivan con DEMO_CONTROLS_ENABLED=false.
 */
@RestController
@RequestMapping("/api/v1/mock")
@ConditionalOnProperty(name = "app.demo.controls-enabled", havingValue = "true", matchIfMissing = true)
public class DemoController {

    private final ChaosService chaos;
    private final Simulator simulator;
    private final BroadcastService broadcast;
    private final VerificationSocketHandler verificationSockets;
    private final JurySocketHandler jurySockets;
    private final VerificationService verification;
    private final CatalogService catalog;
    private final StringRedisTemplate redis;
    private final InstanceInfo instance;

    public DemoController(ChaosService chaos, Simulator simulator, BroadcastService broadcast, VerificationSocketHandler verificationSockets,
                          JurySocketHandler jurySockets, VerificationService verification, CatalogService catalog,
                          StringRedisTemplate redis, InstanceInfo instance) {
        this.chaos = chaos;
        this.simulator = simulator;
        this.broadcast = broadcast;
        this.verificationSockets = verificationSockets;
        this.jurySockets = jurySockets;
        this.verification = verification;
        this.catalog = catalog;
        this.redis = redis;
        this.instance = instance;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Object reported = redis.opsForHash().get("pub:department", "_reported");
        return Map.of(
                "instanceId", instance.id(),
                "chaos", Map.of("redisDown", chaos.redisDown(), "dropResponseRate", chaos.dropResponseRate()),
                "simulator", Map.of("running", simulator.running(), "intervalMs", simulator.intervalMs()),
                "tablesTotal", catalog.tablesTotal("department"),
                "tablesReported", reported == null ? 0 : Long.parseLong(String.valueOf(reported)),
                "doubtfulCases", verification.queueSize());
    }

    @PostMapping("/redis")
    public Map<String, Object> redis(@RequestBody Map<String, Object> body) {
        chaos.setRedisDown(Boolean.FALSE.equals(body.get("up")));
        return Map.of("redisDown", chaos.redisDown(), "dropResponseRate", chaos.dropResponseRate());
    }

    @PostMapping("/chaos")
    public Map<String, Object> chaos(@RequestBody Map<String, Object> body) {
        if (body.get("dropResponseRate") instanceof Number n) chaos.setDropResponseRate(n.doubleValue());
        return Map.of("redisDown", chaos.redisDown(), "dropResponseRate", chaos.dropResponseRate());
    }

    @PostMapping("/sim")
    public Map<String, Object> sim(@RequestBody Map<String, Object> body) {
        Integer interval = body.get("intervalMs") instanceof Number n ? n.intValue() : null;
        Boolean running = body.get("running") instanceof Boolean b ? b : null;
        simulator.configure(running, interval);
        return Map.of("running", simulator.running(), "intervalMs", simulator.intervalMs());
    }

    /** Mata las conexiones de ESTA instancia, como si se cayera (RF-10). */
    @PostMapping("/kill")
    public Map<String, Object> kill() {
        return Map.of("killed", true, "instanceId", instance.id(),
                "sse", broadcast.closeAll(), "ws", verificationSockets.closeAll() + jurySockets.closeAll());
    }

    /** Llena el búfer de los clientes SSE de esta instancia para demostrar la política de clientes lentos (RF-9). */
    @PostMapping("/flood")
    public Map<String, Object> flood(@RequestBody(required = false) Map<String, Object> body) {
        int frames = body != null && body.get("frames") instanceof Number n ? n.intValue() : 400;
        int bytes = body != null && body.get("bytes") instanceof Number n ? n.intValue() : 1024;
        broadcast.flood(Math.min(frames, 5000), Math.min(bytes, 16_384));
        return Map.of("flooded", true, "frames", frames, "bytes", bytes);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        redis.execute((RedisCallback<Object>) c -> {
            c.serverCommands().flushDb();
            return null;
        });
        simulator.forget();
        broadcast.closeAll();
        verificationSockets.closeAll();
        return Map.of("reset", true);
    }
}
