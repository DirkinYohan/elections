package com.elections.infrastructure.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Al arrancar verifica la conexión a Supabase (PostgreSQL) y crea las tablas si no existen.
 * Si la base no responde la aplicación arranca igual: el preconteo vive en Redis y Postgres
 * solo guarda el registro durable de actas y la bitácora de auditoría.
 */
@Component
public class DatabaseHealthCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthCheck.class);
    private final JdbcTemplate jdbc;
    private final PersistenceService persistence;
    private final boolean enabled;

    public DatabaseHealthCheck(JdbcTemplate jdbc, PersistenceService persistence, @Value("${app.db.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.persistence = persistence;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Persistencia en PostgreSQL desactivada (DB_ENABLED=false)");
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                String version = jdbc.queryForObject("select version()", String.class);
                jdbc.execute("""
                        create table if not exists ballot_submissions (
                          id bigserial primary key,
                          acta text not null,
                          version int not null,
                          status text not null,
                          receipt text,
                          jurado text,
                          mesa int, puesto int, municipio int,
                          votos jsonb,
                          sufragantes int,
                          instance_id text,
                          registered_at timestamptz not null default now(),
                          unique (acta, version)
                        )""");
                jdbc.execute("""
                        create table if not exists audit_events (
                          id bigserial primary key,
                          event_type text not null,
                          ballot_id text,
                          responsible text,
                          details text,
                          instance_id text,
                          created_at timestamptz not null default now()
                        )""");
                jdbc.execute("create index if not exists audit_events_ballot_idx on audit_events (ballot_id)");
                persistence.markReady();
                log.info("Conectado a Supabase PostgreSQL: {}", version);
            } catch (RuntimeException e) {
                log.warn("PostgreSQL no disponible; se sigue sin persistencia durable: {}", e.getMessage());
            }
        });
    }
}
