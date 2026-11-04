package com.elections.modules.verification;

import com.elections.modules.verification.internal.dto.CaseResolutionDto;

import java.util.List;
import java.util.Map;

/**
 * Public API for the Electoral Verification Module.
 * Collaborative priority queue for suspicious ballots with exclusive distributed locking.
 */
public interface VerificationApi {

    List<Map<String, Object>> listSuspiciousCasesQueue();

    boolean acquireCaseExclusiveLock(String ballotId, String auditorId);

    boolean releaseCaseLock(String ballotId, String auditorId);

    boolean resolveCase(CaseResolutionDto resolution);
}

