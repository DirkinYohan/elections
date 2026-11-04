package com.elections.modules.audit;

import com.elections.modules.audit.internal.dto.AuditEventDto;

import java.util.List;

/**
 * Public API for the Audit & Traceability Module.
 * Records and retrieves an immutable audit log of electoral events (ballots, corrections, verifications).
 */
public interface AuditApi {

    void recordEvent(AuditEventDto event);

    List<AuditEventDto> getBallotTraceability(String ballotId);
}

