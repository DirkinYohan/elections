package com.elections.infrastructure.database;

import com.elections.infrastructure.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Registro durable en PostgreSQL (Supabase). Se escribe en segundo plano y fuera del camino crítico:
 * la confirmación al jurado depende de Redis (fuente de verdad del preconteo), no de esta escritura.
 * Un semáforo limita las escrituras simultáneas al tamaño del pool de conexiones.
 */
@Service
public class PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);
    private final JdbcTemplate jdbc;
    private final InstanceInfo instance;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits = new Semaphore(4);
    private volatile boolean ready;

    public PersistenceService(JdbcTemplate jdbc, InstanceInfo instance) {
        this.jdbc = jdbc;
        this.instance = instance;
    }

    void markReady() {
        ready = true;
    }

    public boolean ready() {
        return ready;
    }

    public void saveSubmission(Map<String, Object> sheet, String status, String receipt, String votosJson) {
        run(() -> jdbc.update("""
                        insert into ballot_submissions (acta, version, status, receipt, jurado, mesa, puesto, municipio, votos, sufragantes, instance_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                        on conflict (acta, version) do nothing""",
                sheet.get("acta"), sheet.get("version"), status, receipt, sheet.get("jurado"),
                sheet.get("mesa"), sheet.get("puesto"), sheet.get("municipio"), votosJson,
                sheet.get("sufragantes"), instance.id()));
    }

    public void audit(String type, String ballotId, String responsible, String details) {
        run(() -> jdbc.update("insert into audit_events (event_type, ballot_id, responsible, details, instance_id) values (?, ?, ?, ?, ?)",
                type, ballotId, responsible, details, instance.id()));
    }

    public List<Map<String, Object>> auditTrail(String ballotId) {
        if (!ready) return List.of();
        return jdbc.queryForList("""
                select event_type as "eventType", ballot_id as "ballotId", responsible as "responsibleUser",
                       details, instance_id as "instanceId", created_at as "createdAt"
                from audit_events where ballot_id = ? order by id""", ballotId);
    }

    private void run(Runnable task) {
        if (!ready) return;
        executor.execute(() -> {
            try {
                permits.acquire();
                try {
                    task.run();
                } finally {
                    permits.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("No se pudo escribir en PostgreSQL: {}", e.getMessage());
            }
        });
    }
}
