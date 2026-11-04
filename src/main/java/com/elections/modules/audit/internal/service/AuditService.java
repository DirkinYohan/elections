package com.elections.modules.audit.internal.service;

import com.elections.modules.audit.AuditApi;
import com.elections.modules.audit.internal.dto.AuditEventDto;
import com.elections.modules.audit.internal.model.AuditRecord;
import com.elections.modules.audit.internal.repository.AuditRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditService implements AuditApi {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public void recordEvent(AuditEventDto event) {
        AuditRecord r = new AuditRecord(
                UUID.randomUUID().toString(),
                event.getEventType(),
                event.getBallotId(),
                event.getResponsibleUser(),
                event.getDetails()
        );
        auditRepository.save(r);
    }

    @Override
    public List<AuditEventDto> getBallotTraceability(String ballotId) {
        return auditRepository.findByBallotId(ballotId).stream()
                .map(r -> new AuditEventDto(r.getEventType(), r.getBallotId(), r.getResponsibleUser(), r.getDetails()))
                .collect(Collectors.toList());
    }
}

