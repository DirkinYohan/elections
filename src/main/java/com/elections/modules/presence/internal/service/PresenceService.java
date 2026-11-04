package com.elections.modules.presence.internal.service;

import com.elections.modules.presence.PresenceApi;
import com.elections.modules.presence.internal.dto.HeartbeatDto;
import com.elections.modules.presence.internal.repository.PresenceRedisRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class PresenceService implements PresenceApi {

    private final PresenceRedisRepository presenceRedisRepository;
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);

    public PresenceService(PresenceRedisRepository presenceRedisRepository) {
        this.presenceRedisRepository = presenceRedisRepository;
    }

    @Override
    public void registerHeartbeat(HeartbeatDto heartbeat) {
        String status = heartbeat.getCurrentCaseId() != null ? "BUSY:" + heartbeat.getCurrentCaseId() : "AVAILABLE";
        presenceRedisRepository.updateHeartbeat(heartbeat.getAuditorId(), status, HEARTBEAT_TTL);
    }

    @Override
    public void disconnectAuditor(String auditorId) {
        presenceRedisRepository.delete(auditorId);
    }

    @Override
    public List<Map<String, Object>> listActiveAuditors() {
        Set<String> keys = presenceRedisRepository.getActiveKeys();
        List<Map<String, Object>> list = new ArrayList<>();
        if (keys != null) {
            for (String k : keys) {
                Map<String, Object> map = new HashMap<>();
                map.put("key", k);
                list.add(map);
            }
        }
        return list;
    }
}

