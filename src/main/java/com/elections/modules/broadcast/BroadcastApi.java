package com.elections.modules.broadcast;

/**
 * Public API for the Real-Time Broadcast Module.
 * Queues updates for batch dispatch, handles backpressure, and prevents Event Loop freezes.
 */
public interface BroadcastApi {

    void queueUpdate(String topic, Object data);

    void dispatchWindowBatch();

    void registerSlowClient(String sessionId);
}

