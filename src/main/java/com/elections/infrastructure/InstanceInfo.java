package com.elections.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Identidad de esta instancia (RT-1). Ninguna instancia conoce a las demás: solo se ven a través de Redis. */
@Component
public class InstanceInfo {

    private final String id;
    private final AtomicLong connectionSeq = new AtomicLong();

    public InstanceInfo(@Value("${app.instance-id:}") String configured) {
        this.id = configured == null || configured.isBlank()
                ? "inst-" + UUID.randomUUID().toString().substring(0, 8)
                : configured;
    }

    public String id() {
        return id;
    }

    /** Identificador de conexión único en todo el sistema (instancia + secuencia). */
    public String nextConnectionId() {
        return id + "#" + connectionSeq.incrementAndGet();
    }
}
