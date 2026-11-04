package com.elections.modules.results;

import com.elections.infrastructure.redis.LuaScripts;
import com.elections.modules.catalog.CatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Lectura de acumulados (RF-3, RF-5, RF-6).
 *
 * Estructuras en Redis por tópico (department, municipality:<id>, station:<id>, table:<mesa>):
 *  - tot:<topic>  HASH  voto → total y _reported (mesas informadas). HINCRBY O(1) al aplicar; HGETALL O(campos) al leer.
 *  - pub:<topic>  HASH  copia de tot:<topic> tal como quedó en el último evento publicado: lo que muestra un snapshot.
 *  - rank:<topic> ZSET  candidato → votos. El ranking sale con ZREVRANGE sin recorrer todas las mesas.
 */
@Service
public class ResultsService {

    public record Resume(String kind, String lastId, Map<String, Object> totals, List<String[]> events) {}

    private final StringRedisTemplate redis;
    private final LuaScripts lua;
    private final CatalogService catalog;
    private final int maxReplay;

    public ResultsService(StringRedisTemplate redis, LuaScripts lua, CatalogService catalog,
                          @Value("${app.broadcast.topic-retention:300}") int maxReplay) {
        this.redis = redis;
        this.lua = lua;
        this.catalog = catalog;
        this.maxReplay = maxReplay;
    }

    /** Totales publicados de un tópico, con todas las claves de voto (0 si no hay datos). */
    public Map<String, Object> publishedTotals(String topic) {
        Map<Object, Object> raw = redis.opsForHash().entries("pub:" + topic);
        List<String> flat = new ArrayList<>();
        raw.forEach((k, v) -> { flat.add(String.valueOf(k)); flat.add(String.valueOf(v)); });
        return totalsFromFlat(topic, flat, 0);
    }

    public Map<String, Object> totalsFromFlat(String topic, List<String> flat, int from) {
        Map<String, Long> votes = new LinkedHashMap<>();
        for (String k : CatalogService.VOTE_KEYS) votes.put(k, 0L);
        long reported = 0;
        for (int i = from; i + 1 < flat.size(); i += 2) {
            long v = Long.parseLong(flat.get(i + 1));
            if ("_reported".equals(flat.get(i))) reported = v;
            else votes.put(flat.get(i), v);
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("votes", votes);
        totals.put("tablesReported", reported);
        totals.put("tablesTotal", catalog.tablesTotal(topic));
        return totals;
    }

    /** Qué entregar a un cliente que se conecta o reconecta (RF-6). Ver resume.lua. */
    public Resume resume(String topic, String lastEventId) {
        List<String> r = lua.resume(topic, lastEventId == null ? "" : lastEventId, String.valueOf(maxReplay));
        return switch (r.get(0)) {
            case "UPTODATE" -> new Resume("UPTODATE", r.get(1), null, List.of());
            case "REPLAY" -> {
                List<String[]> events = new ArrayList<>();
                for (int i = 1; i + 2 < r.size(); i += 3) {
                    events.add(new String[]{r.get(i), r.get(i + 1), r.get(i + 2)});
                }
                String last = events.isEmpty() ? lastEventId : events.get(events.size() - 1)[0];
                yield new Resume("REPLAY", last, null, events);
            }
            default -> new Resume("SNAPSHOT", r.get(1), totalsFromFlat(topic, r, 2), List.of());
        };
    }

    /** AggregateDto de los endpoints REST (transporte polling, para la comparación de la sección 7). */
    public Map<String, Object> aggregate(String level, String identifier, String topic) {
        Map<String, Object> t = publishedTotals(topic);
        long reported = ((Number) t.get("tablesReported")).longValue();
        int total = ((Number) t.get("tablesTotal")).intValue();
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("level", level);
        dto.put("identifier", identifier);
        dto.put("reportedTables", reported);
        dto.put("totalTables", total);
        dto.put("reportedPercentage", total > 0 ? reported * 100.0 / total : 0.0);
        dto.put("candidateTotals", t.get("votes"));
        dto.put("ranking", ranking(topic));
        return dto;
    }

    public List<Map<String, Object>> ranking(String topic) {
        var set = redis.opsForZSet().reverseRangeWithScores("rank:" + topic, 0, -1);
        List<Map<String, Object>> out = new ArrayList<>();
        if (set != null) for (var e : set) out.add(Map.of("key", String.valueOf(e.getValue()), "votes", Math.round(e.getScore() == null ? 0 : e.getScore())));
        return out;
    }
}
