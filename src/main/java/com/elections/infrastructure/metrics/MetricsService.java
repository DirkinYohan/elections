package com.elections.infrastructure.metrics;

import com.elections.infrastructure.InstanceInfo;
import com.elections.infrastructure.Json;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * Instrumentación de cada instancia (RT-7). Cada segundo se toma una muestra y se publica en Redis,
 * para que GET /metrics en cualquier instancia muestre las ocho.
 */
@Service
public class MetricsService {

    private static final String INSTANCES = "metrics:instances";
    private final StringRedisTemplate redis;
    private final InstanceInfo instance;

    public final AtomicLong messages = new AtomicLong();
    public final AtomicLong messagesTotal = new AtomicLong();
    public final AtomicLong naiveTotal = new AtomicLong();
    public final AtomicLong events = new AtomicLong();
    public final AtomicLong sseConnects = new AtomicLong();
    public final AtomicLong reconnects = new AtomicLong();
    public final AtomicLong wsConnects = new AtomicLong();
    public final AtomicLong slowDisconnects = new AtomicLong();
    private final AtomicLong latencySum = new AtomicLong();
    private final AtomicLong latencyCount = new AtomicLong();
    private final ConcurrentLinkedDeque<Long> ballotTimes = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> history = new ArrayDeque<>();

    private volatile IntSupplier sseCount = () -> 0;
    private volatile IntSupplier wsCount = () -> 0;
    private volatile java.util.function.DoubleSupplier loopLag = () -> 0;
    private volatile Map<String, Object> last = Map.of();
    private long baselineMemory = usedMemory();
    private long lastApplied = -1;

    public MetricsService(StringRedisTemplate redis, InstanceInfo instance) {
        this.redis = redis;
        this.instance = instance;
    }

    public void bindSources(IntSupplier sse, IntSupplier ws, java.util.function.DoubleSupplier lag) {
        if (sse != null) this.sseCount = sse;
        if (ws != null) this.wsCount = ws;
        if (lag != null) this.loopLag = lag;
    }

    public void bindWs(IntSupplier ws) {
        this.wsCount = ws;
    }

    public void recordLatency(long ms) {
        latencySum.addAndGet(Math.max(0, ms));
        latencyCount.incrementAndGet();
    }

    public void recordBallot() {
        ballotTimes.add(System.currentTimeMillis());
    }

    @Scheduled(fixedRate = 1000)
    void sample() {
        long now = System.currentTimeMillis();
        while (!ballotTimes.isEmpty() && now - ballotTimes.peekFirst() > 60_000) ballotTimes.pollFirst();
        int sse = sseCount.getAsInt();
        int ws = wsCount.getAsInt();
        long used = usedMemory();
        if (sse + ws == 0) baselineMemory = used;
        long lc = latencyCount.getAndSet(0);
        long ls = latencySum.getAndSet(0);

        // Cifra ingenua (RF-4): un mensaje por acta aplicada y por cliente conectado.
        try {
            String raw = redis.opsForValue().get("stat:applied");
            long applied = raw == null ? 0 : Long.parseLong(raw);
            if (lastApplied >= 0 && applied > lastApplied) naiveTotal.addAndGet((applied - lastApplied) * sse);
            lastApplied = applied;
        } catch (RuntimeException ignored) {
            // Redis caído: se omite este segundo
        }

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("t", now);
        s.put("instanceId", instance.id());
        s.put("sseConnections", sse);
        s.put("wsConnections", ws);
        s.put("messagesPerSecond", messages.getAndSet(0));
        s.put("eventLoopLagMs", Math.round(loopLag.getAsDouble() * 10) / 10.0);
        s.put("memoryPerConnectionBytes", sse + ws > 0 ? Math.max(0, (used - baselineMemory) / (sse + ws)) : 0);
        s.put("ballotsPerMinute", ballotTimes.size());
        s.put("publishLatencyMs", lc > 0 ? ls / lc : 0);
        s.put("reconnectsPerSecond", reconnects.getAndSet(0));
        s.put("connectsPerSecond", sseConnects.getAndSet(0) + wsConnects.getAndSet(0));
        s.put("slowDisconnects", slowDisconnects.getAndSet(0));
        s.put("eventsPerSecond", events.getAndSet(0));
        s.put("messagesTotal", messagesTotal.get());
        s.put("naiveMessagesTotal", naiveTotal.get());
        s.put("heapUsedBytes", used);
        last = s;
        synchronized (history) {
            history.addLast(s);
            while (history.size() > 300) history.removeFirst();
        }
        try {
            redis.opsForValue().set("metrics:inst:" + instance.id(), Json.write(s), Duration.ofSeconds(10));
            redis.opsForZSet().add(INSTANCES, instance.id(), now);
            String hk = "metrics:hist:" + instance.id();
            redis.opsForList().rightPush(hk, Json.write(s));
            redis.opsForList().trim(hk, -300, -1);
            redis.expire(hk, Duration.ofMinutes(10));
        } catch (RuntimeException ignored) {
            // sin Redis no hay vista global, la local sigue disponible
        }
    }

    /** Vista global: las instancias vivas y la historia sumada por segundo. */
    public Map<String, Object> globalView() {
        List<Map<String, Object>> instances = new ArrayList<>();
        Map<Long, Map<String, Object>> merged = new TreeMap<>();
        try {
            long now = System.currentTimeMillis();
            redis.opsForZSet().removeRangeByScore(INSTANCES, 0, now - 15_000);
            Set<String> ids = redis.opsForZSet().range(INSTANCES, 0, -1);
            for (String id : ids == null ? Set.<String>of() : ids) {
                String raw = redis.opsForValue().get("metrics:inst:" + id);
                if (raw != null) instances.add(Json.readMap(raw));
                List<String> hist = redis.opsForList().range("metrics:hist:" + id, -120, -1);
                for (String h : hist == null ? List.<String>of() : hist) mergeSample(merged, Json.readMap(h));
            }
        } catch (RuntimeException e) {
            instances = List.of(last);
            synchronized (history) {
                for (Map<String, Object> h : history) mergeSample(merged, h);
            }
        }
        instances.sort(Comparator.comparing(m -> String.valueOf(m.get("instanceId"))));
        return Map.of("instances", instances, "history", new ArrayList<>(merged.values()));
    }

    private static final Set<String> MAX_FIELDS = Set.of("eventLoopLagMs", "publishLatencyMs", "memoryPerConnectionBytes");

    private static void mergeSample(Map<Long, Map<String, Object>> merged, Map<String, Object> s) {
        long second = ((Number) s.get("t")).longValue() / 1000 * 1000;
        Map<String, Object> m = merged.computeIfAbsent(second, k -> new LinkedHashMap<>(Map.of("t", k)));
        for (var e : s.entrySet()) {
            if (!(e.getValue() instanceof Number n) || e.getKey().equals("t")) continue;
            double prev = m.get(e.getKey()) instanceof Number p ? p.doubleValue() : 0;
            double v = MAX_FIELDS.contains(e.getKey()) ? Math.max(prev, n.doubleValue()) : prev + n.doubleValue();
            m.put(e.getKey(), Math.round(v * 10) / 10.0);
        }
    }

    private static long usedMemory() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }
}
