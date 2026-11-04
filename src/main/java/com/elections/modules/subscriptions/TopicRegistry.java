package com.elections.modules.subscriptions;

import com.elections.modules.broadcast.SseConnection;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Relación tópico → conexiones de ESTA instancia (RF-5). Mapa hash de conjuntos concurrentes:
 * alta O(1), baja O(1) y recorrido O(suscriptores del tópico), nunca O(conexiones de la instancia).
 */
@Component
public class TopicRegistry {

    private final Map<String, Set<SseConnection>> byTopic = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    public void add(SseConnection c) {
        if (byTopic.computeIfAbsent(c.topic(), k -> ConcurrentHashMap.newKeySet()).add(c)) total.incrementAndGet();
    }

    public void remove(SseConnection c) {
        Set<SseConnection> set = byTopic.get(c.topic());
        if (set != null && set.remove(c)) total.decrementAndGet();
    }

    public SseConnection[] subscribers(String topic) {
        Set<SseConnection> set = byTopic.get(topic);
        return set == null ? new SseConnection[0] : set.toArray(new SseConnection[0]);
    }

    public int size() {
        return total.get();
    }

    public List<SseConnection> all() {
        return byTopic.values().stream().flatMap(Collection::stream).toList();
    }

    public Map<String, Integer> countsByTopic() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        byTopic.forEach((k, v) -> { if (!v.isEmpty()) out.put(k, v.size()); });
        return out;
    }
}
