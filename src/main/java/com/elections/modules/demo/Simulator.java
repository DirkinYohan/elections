package com.elections.modules.demo;

import com.elections.infrastructure.InstanceInfo;
import com.elections.modules.ballots.BallotService;
import com.elections.modules.catalog.CatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulador de jurados para la demostración: envía actas por el mismo camino que la API
 * (~7 % dudosas, ~8 % reenvíos con el mismo idem, ~2 % correcciones). Solo corre en una instancia
 * (la que tiene sim:leader en Redis) y su configuración vive en Redis para que todas la vean.
 */
@Component
public class Simulator {

    private final BallotService ballots;
    private final CatalogService catalog;
    private final StringRedisTemplate redis;
    private final InstanceInfo instance;
    private final boolean autostart;
    private final int defaultInterval;
    private final Map<Integer, Map<String, Object>> sent = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile int intervalMs;
    private long nextAt;

    public Simulator(BallotService ballots, CatalogService catalog, StringRedisTemplate redis, InstanceInfo instance,
                     @Value("${app.demo.simulator-autostart:true}") boolean autostart,
                     @Value("${app.demo.simulator-interval-ms:400}") int defaultInterval) {
        this.ballots = ballots;
        this.catalog = catalog;
        this.redis = redis;
        this.instance = instance;
        this.autostart = autostart;
        this.defaultInterval = defaultInterval;
        this.intervalMs = defaultInterval;
    }

    public boolean running() { return running; }
    public int intervalMs() { return intervalMs; }

    public void configure(Boolean run, Integer interval) {
        if (run != null) redis.opsForHash().put("sim", "running", String.valueOf(run));
        if (interval != null && interval >= 20) redis.opsForHash().put("sim", "intervalMs", String.valueOf(interval));
        refresh();
    }

    public void forget() {
        sent.clear();
    }

    @Scheduled(fixedDelay = 1000)
    void refresh() {
        try {
            if (autostart) redis.opsForHash().putIfAbsent("sim", "running", "true");
            Map<Object, Object> cfg = redis.opsForHash().entries("sim");
            running = Boolean.parseBoolean(String.valueOf(cfg.getOrDefault("running", "false")));
            intervalMs = Integer.parseInt(String.valueOf(cfg.getOrDefault("intervalMs", String.valueOf(defaultInterval))));
        } catch (RuntimeException ignored) {
            // Redis caído
        }
    }

    @Scheduled(fixedRate = 20)
    void tick() {
        if (!running) return;
        long now = System.currentTimeMillis();
        if (now < nextAt) return;
        nextAt = now + intervalMs;
        try {
            Boolean leader = redis.opsForValue().setIfAbsent("sim:leader", instance.id(), Duration.ofSeconds(3));
            if (!Boolean.TRUE.equals(leader) && !instance.id().equals(redis.opsForValue().get("sim:leader"))) return;
            redis.expire("sim:leader", Duration.ofSeconds(3));
            step();
        } catch (RuntimeException ignored) {
            // Redis caído o simulado caído: el simulador también recibe 503
        }
    }

    private void step() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double p = r.nextDouble();
        List<Map<String, Object>> previous = new ArrayList<>(sent.values());
        if (p < 0.08 && !previous.isEmpty()) {
            ballots.submit(previous.get(r.nextInt(previous.size()))); // reenvío con el mismo idem
            return;
        }
        if (p < 0.10 && !previous.isEmpty()) {
            Map<String, Object> prev = previous.get(r.nextInt(previous.size()));
            Map<String, Object> fixed = sheet((Integer) prev.get("mesa"), (Integer) prev.get("version") + 1, false);
            sent.put((Integer) prev.get("mesa"), fixed);
            ballots.submit(fixed); // corrección (RF-2)
            return;
        }
        List<CatalogService.Table> pending = catalog.tables().stream().filter(t -> !sent.containsKey(t.mesa())).toList();
        if (pending.isEmpty()) return;
        CatalogService.Table t = pending.get(r.nextInt(pending.size()));
        Map<String, Object> sheet = sheet(t.mesa(), 1, r.nextDouble() < 0.07);
        sent.put(t.mesa(), sheet);
        ballots.submit(sheet);
    }

    private Map<String, Object> sheet(int mesa, int version, boolean doubtful) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        CatalogService.Table t = catalog.table(mesa).orElseThrow();
        Map<String, Object> votos = new LinkedHashMap<>();
        votos.put("C1", r.nextInt(40, 261));
        votos.put("C2", r.nextInt(40, 261));
        votos.put("C3", r.nextInt(40, 261));
        votos.put("BLANCO", r.nextInt(0, 26));
        votos.put("NULO", r.nextInt(0, 13));
        votos.put("NO_MARCADO", r.nextInt(0, 7));
        int sum = votos.values().stream().mapToInt(v -> (Integer) v).sum();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("acta", CatalogService.actaId(t.puesto(), mesa));
        s.put("mesa", mesa);
        s.put("puesto", t.puesto());
        s.put("municipio", t.municipio());
        s.put("version", version);
        s.put("jurado", "jury" + (1 + (mesa - 1) % 6400));
        s.put("ts_cierre", System.currentTimeMillis() / 1000);
        s.put("votos", votos);
        s.put("sufragantes", doubtful ? sum + r.nextInt(1, 5) : sum);
        s.put("hash_foto", UUID.randomUUID().toString().replace("-", ""));
        s.put("idem", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return s;
    }
}
