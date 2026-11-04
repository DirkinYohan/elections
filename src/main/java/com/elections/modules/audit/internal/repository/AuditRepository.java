package com.elections.modules.audit.internal.repository;

import com.elections.modules.audit.internal.model.AuditRecord;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AuditRepository {

    private final List<AuditRecord> auditLog = Collections.synchronizedList(new ArrayList<>());

    public void save(AuditRecord record) {
        auditLog.add(record);
    }

    public List<AuditRecord> findByBallotId(String ballotId) {
        synchronized (auditLog) {
            return auditLog.stream()
                    .filter(r -> ballotId.equals(r.getBallotId()))
                    .collect(Collectors.toList());
        }
    }
}

