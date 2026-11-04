package com.elections.infrastructure.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Verifica al arrancar que la conexión a Supabase (PostgreSQL) funciona. */
@Component
public class DatabaseHealthCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthCheck.class);
    private final JdbcTemplate jdbc;

    public DatabaseHealthCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String version = jdbc.queryForObject("select version()", String.class);
        log.info("Conectado a Supabase PostgreSQL: {}", version);
    }
}
