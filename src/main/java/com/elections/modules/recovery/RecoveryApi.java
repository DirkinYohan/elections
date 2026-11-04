package com.elections.modules.recovery;

import com.elections.modules.recovery.internal.dto.StateRecoveryDto;

/**
 * Public API for the Continuity & Recovery Module.
 * Replays missed events via monotonic IDs or delivers a full snapshot if too far behind.
 */
public interface RecoveryApi {

    StateRecoveryDto recoverFrom(String topic, long clientLastMonotonicId);

    long recordHistoryEvent(String topic, Object data);
}

