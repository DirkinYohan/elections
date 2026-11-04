package com.elections.modules.subscriptions;

import java.util.Set;

/**
 * Public API for the Subscriptions & Topics Module.
 * Tracks client sessions per geographic topic (department, municipality, station).
 */
public interface SubscriptionsApi {

    void subscribe(String sessionId, String topic);

    void unsubscribe(String sessionId, String topic);

    void removeSession(String sessionId);

    Set<String> getSubscribers(String topic);
}

