package com.elections.modules.recovery.internal.service;

import com.elections.modules.recovery.RecoveryApi;
import com.elections.modules.recovery.internal.dto.StateRecoveryDto;
import com.elections.modules.recovery.internal.repository.StreamHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RecoveryService implements RecoveryApi {

    private final StreamHistoryRepository streamHistoryRepository;

    public RecoveryService(StreamHistoryRepository streamHistoryRepository) {
        this.streamHistoryRepository = streamHistoryRepository;
    }

    @Override
    public StateRecoveryDto recoverFrom(String topic, long clientLastMonotonicId) {
        StateRecoveryDto dto = new StateRecoveryDto();
        long currentMonotonic = streamHistoryRepository.nextMonotonicId();

        // RF-6: If the client is too far behind (> 100 missed events), deliver full SNAPSHOT
        if (currentMonotonic - clientLastMonotonicId > 100 || clientLastMonotonicId <= 0) {
            dto.setType("SNAPSHOT");
            dto.setLastMonotonicId(currentMonotonic);
            dto.setSnapshot("CURRENT_SNAPSHOT_TOPIC_" + topic);
        } else {
            dto.setType("DELTAS");
            dto.setLastMonotonicId(currentMonotonic);
            dto.setDeltas(Collections.emptyList());
        }
        return dto;
    }

    @Override
    public long recordHistoryEvent(String topic, Object data) {
        return streamHistoryRepository.nextMonotonicId();
    }
}

