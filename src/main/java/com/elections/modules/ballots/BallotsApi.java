package com.elections.modules.ballots;

import com.elections.modules.ballots.internal.dto.BallotTransmissionDto;

import java.util.Map;

/**
 * Public API for the Ballots Management Module.
 * Handles transmission reception, idempotency validation, and accounting checksums.
 */
public interface BallotsApi {

    Map<String, Object> processTransmission(BallotTransmissionDto dto);

    boolean existsBallot(String ballotId);
}

