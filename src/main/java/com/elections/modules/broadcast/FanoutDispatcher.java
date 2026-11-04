package com.elections.modules.broadcast;

import com.elections.infrastructure.metrics.MetricsService;
import com.elections.modules.subscriptions.TopicRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fan-out por lotes (RF-4, RT-4).
 *
 * Es el equivalente al Event Loop de Node: un único hilo ("fanout-loop") reparte cada evento a los
 * suscriptores del tópico en lotes de N conexiones. Al terminar un lote no sigue con el siguiente:
 * lo vuelve a encolar al final de la cola del hilo, así otras tareas (otros tópicos, pings, cierres)
 * se intercalan y nadie queda congelado mientras se recorren 50.000 conexiones.
 *
 * El retraso del loop se mide igual que en Node: cada 50 ms se programa una tarea en el hilo y se
 * mide cuánto tardó en empezar. Ese valor (el máximo de cada segundo) es "eventLoopLagMs" en el tablero.
 */
@Component
public class FanoutDispatcher {

    private final TopicRegistry registry;
    private final MetricsService metrics;
    private final int batch;
    private final long slowBytes;
    private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> new Thread(r, "fanout-loop"));
    private final ScheduledExecutorService probe = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "fanout-lag-probe"));
    private final AtomicLong maxLagMicros = new AtomicLong();

    public FanoutDispatcher(TopicRegistry registry, MetricsService metrics,
                            @Value("${app.broadcast.fanout-batch:500}") int batch,
                            @Value("${app.broadcast.slow-client-bytes:262144}") long slowBytes) {
        this.registry = registry;
        this.metrics = metrics;
        this.batch = batch;
        this.slowBytes = slowBytes;
        probe.scheduleAtFixedRate(() -> {
            long posted = System.nanoTime();
            loop.execute(() -> maxLagMicros.accumulateAndGet((System.nanoTime() - posted) / 1000, Math::max));
        }, 50, 50, TimeUnit.MILLISECONDS);
        metrics.bindSources(registry::size, null, () -> maxLagMicros.getAndSet(0) / 1000.0);
    }

    public void dispatch(String topic, SseConnection.Frame frame) {
        loop.execute(() -> sendBatch(registry.subscribers(topic), 0, frame));
    }

    private void sendBatch(SseConnection[] targets, int from, SseConnection.Frame frame) {
        int end = Math.min(from + batch, targets.length);
        for (int i = from; i < end; i++) {
            SseConnection c = targets[i];
            if (c.isClosed()) continue;
            if (c.pendingBytes() > slowBytes) {
                // RF-9: el cliente no consume al ritmo del servidor. Se le deja de enviar y se cierra;
                // vuelve con su lastEventId y recibe lo perdido o una fotografía del estado.
                metrics.slowDisconnects.incrementAndGet();
                c.close();
                continue;
            }
            c.offer(frame);
            metrics.messages.incrementAndGet();
            metrics.messagesTotal.incrementAndGet();
        }
        if (end < targets.length) loop.execute(() -> sendBatch(targets, end, frame)); // ceder el loop
    }

    /** Ejecuta una tarea en el loop de fan-out (por ejemplo, un ping a todas las conexiones). */
    public void runOnLoop(Runnable task) {
        loop.execute(task);
    }

    @PreDestroy
    void stop() {
        probe.shutdownNow();
        loop.shutdownNow();
    }
}
