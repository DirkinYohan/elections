package com.elections.modules.presence;

import com.elections.modules.presence.internal.dto.HeartbeatDto;

import java.util.List;
import java.util.Map;

/**
 * Public API for the Presence Module.
 * Tracks connected auditors, heartbeats, and evicts ghost connections via Redis TTL.
 */
public interface PresenceApi {

    void registerHeartbeat(HeartbeatDto heartbeat);

    void disconnectAuditor(String auditorId);

    List<Map<String, Object>> listActiveAuditors();
}

