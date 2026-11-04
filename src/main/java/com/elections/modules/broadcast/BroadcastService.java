package com.elections.modules.broadcast;

import com.elections.infrastructure.InstanceInfo;
import com.elections.infrastructure.Json;
import com.elections.infrastructure.chaos.ChaosService;
import com.elections.infrastructure.metrics.MetricsService;
import com.elections.infrastructure.redis.LuaScripts;
import com.elections.modules.results.ResultsService;
import com.elections.modules.subscriptions.TopicRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Difusión al público (RF-4, RF-5, RF-6).
 *
 * 1. Ventana: cada WINDOW_MS una sola instancia (la que gana SET flush:leader NX PX) ejecuta flush.lua,
 *    que convierte todos los cambios de la ventana en UN evento por tópico con los valores nuevos.
 * 2. Backplane: ese evento se escribe en el Redis Stream "feed". Cada instancia lo lee con XREAD BLOCK
 *    desde el último id que vio. A diferencia de Pub/Sub, el stream tiene memoria: si una instancia
 *    pierde a Redis unos segundos, al volver sigue desde donde iba y no pierde eventos.
 * 3. Historial: el mismo evento se guarda en stream:<topic> (MAXLEN ~ TOPIC_RETENTION) para que un
 *    cliente que vuelve con lastEventId reciba solo lo que se perdió (RF-6).
 */
@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);
    private final StringRedisTemplate redis;
    private final LuaScripts lua;
    private final TopicRegistry registry;
    private final FanoutDispatcher dispatcher;
    private final ResultsService results;
    private final MetricsService metrics;
    private final ChaosService chaos;
    private final InstanceInfo instance;
    private final long windowMs;
    private final int feedRetention;
    private final int topicRetention;
    private volatile boolean running = true;
    private Thread reader;

    public BroadcastService(StringRedisTemplate redis, LuaScripts lua, TopicRegistry registry, FanoutDispatcher dispatcher,
                            ResultsService results, MetricsService metrics, ChaosService chaos, InstanceInfo instance,
                            @Value("${app.broadcast.window-ms:2000}") long windowMs,
                            @Value("${app.broadcast.feed-retention:20000}") int feedRetention,
                            @Value("${app.broadcast.topic-retention:300}") int topicRetention) {
        this.redis = redis;
        this.lua = lua;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.results = results;
        this.metrics = metrics;
        this.chaos = chaos;
        this.instance = instance;
        this.windowMs = windowMs;
        this.feedRetention = feedRetention;
        this.topicRetention = topicRetention;
    }

    // ---- 1. Ventana de agregación --------------------------------------------------------------

    @Scheduled(fixedRateString = "${app.broadcast.window-ms:2000}")
    void flushWindow() {
        if (chaos.redisDown()) return; // degradado: no se publica nada nuevo
        try {
            Boolean leader = redis.opsForValue().setIfAbsent("flush:leader", instance.id(), Duration.ofMillis(Math.max(200, windowMs - 150)));
            if (!Boolean.TRUE.equals(leader)) return;
            lua.flush(String.valueOf(System.currentTimeMillis()), String.valueOf(feedRetention), String.valueOf(topicRetention));
        } catch (RuntimeException e) {
            log.debug("No se pudo cerrar la ventana: {}", e.getMessage());
        }
    }

    // ---- 2. Backplane: lectura del stream global -----------------------------------------------

    @PostConstruct
    void startReader() {
        reader = Thread.ofPlatform().name("feed-reader").daemon().start(this::readLoop);
    }

    private void readLoop() {
        String lastId = null;
        while (running) {
            try {
                if (lastId == null) {
                    List<MapRecord<String, Object, Object>> tail = redis.opsForStream().reverseRange("feed", Range.unbounded(), Limit.limit().count(1));
                    lastId = tail == null || tail.isEmpty() ? "0-0" : tail.get(0).getId().getValue();
                }
                List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                        StreamReadOptions.empty().count(500).block(Duration.ofMillis(1000)),
                        StreamOffset.create("feed", ReadOffset.from(lastId)));
                if (records == null) continue;
                for (MapRecord<String, Object, Object> r : records) {
                    lastId = r.getId().getValue();
                    Map<Object, Object> v = r.getValue();
                    String topic = String.valueOf(v.get("topic"));
                    metrics.events.incrementAndGet();
                    Object firstAt = v.get("firstAt");
                    if (firstAt != null) metrics.recordLatency(System.currentTimeMillis() - Long.parseLong(String.valueOf(firstAt)));
                    dispatcher.dispatch(topic, new SseConnection.Frame(lastId, String.valueOf(v.get("type")), String.valueOf(v.get("data")), false));
                }
            } catch (RuntimeException e) {
                if (!running) return;
                log.debug("Lectura del feed falló, reintento: {}", e.getMessage());
                sleep(1000);
            }
        }
    }

    // ---- 3. Conexión SSE de un cliente ---------------------------------------------------------

    public SseEmitter open(String topic, String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        SseConnection conn = new SseConnection(instance.nextConnectionId(), topic, emitter, registry::remove);
        // Sale de inmediato para que el navegador dispare "open" aunque no haya nada pendiente.
        conn.sendInitial(new SseConnection.Frame(null, null, "connected", true));
        registry.add(conn); // primero se suscribe (los eventos en vivo esperan), luego se arma el estado inicial
        metrics.sseConnects.incrementAndGet();
        if (lastEventId != null && !lastEventId.isBlank()) metrics.reconnects.incrementAndGet();
        try {
            ResultsService.Resume resume = results.resume(topic, lastEventId);
            switch (resume.kind()) {
                case "SNAPSHOT" -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "snapshot");
                    payload.put("topic", topic);
                    payload.put("totals", resume.totals());
                    payload.put("instanceId", instance.id());
                    payload.put("at", System.currentTimeMillis());
                    conn.sendInitial(new SseConnection.Frame(resume.lastId(), "snapshot", Json.write(payload), false));
                }
                case "REPLAY" -> {
                    for (String[] e : resume.events()) conn.sendInitial(new SseConnection.Frame(e[0], e[1], e[2], false));
                }
                default -> { /* al día: nada que reenviar */ }
            }
            conn.finishInit(resume.lastId());
        } catch (RuntimeException e) {
            // Redis no responde: la conexión queda abierta y recibirá los eventos cuando vuelva.
            conn.sendInitial(new SseConnection.Frame(null, null, "degraded", true));
            conn.finishInit(lastEventId);
        }
        return emitter;
    }

    @Scheduled(fixedRate = 15_000)
    void ping() {
        SseConnection.Frame ping = new SseConnection.Frame(null, null, "ping", true);
        dispatcher.runOnLoop(() -> registry.all().forEach(c -> c.enqueue(ping)));
    }

    /** Simula la muerte de la instancia para la demostración de la avalancha (RF-10). */
    public int closeAll() {
        List<SseConnection> all = registry.all();
        all.forEach(SseConnection::close);
        return all.size();
    }

    /** Inunda a los clientes de esta instancia con relleno para demostrar la política de clientes lentos (RF-9). */
    public void flood(int frames, int bytes) {
        String pad = "x".repeat(Math.max(1, bytes));
        SseConnection.Frame f = new SseConnection.Frame(null, null, pad, true);
        for (int i = 0; i < frames; i++) {
            dispatcher.runOnLoop(() -> {
                for (SseConnection c : registry.all()) {
                    if (c.pendingBytes() > 262_144) { metrics.slowDisconnects.incrementAndGet(); c.close(); }
                    else c.enqueue(f);
                }
            });
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        if (reader != null) reader.interrupt();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
