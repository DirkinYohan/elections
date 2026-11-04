package com.elections.modules.subscriptions.internal.repository;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SubscriptionsRepository {

    // Topic -> Session IDs
    private final ConcurrentHashMap<String, Set<String>> topicToSessions = new ConcurrentHashMap<>();
    // Session ID -> Topics
    private final ConcurrentHashMap<String, Set<String>> sessionToTopics = new ConcurrentHashMap<>();

    public void addSubscription(String sessionId, String topic) {
        topicToSessions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionToTopics.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(topic);
    }

    public void removeSubscription(String sessionId, String topic) {
        Set<String> sessions = topicToSessions.get(topic);
        if (sessions != null) {
            sessions.remove(sessionId);
        }
        Set<String> topics = sessionToTopics.get(sessionId);
        if (topics != null) {
            topics.remove(topic);
        }
    }

    public void removeSession(String sessionId) {
        Set<String> topics = sessionToTopics.remove(sessionId);
        if (topics != null) {
            for (String t : topics) {
                Set<String> sessions = topicToSessions.get(t);
                if (sessions != null) {
                    sessions.remove(sessionId);
                }
            }
        }
    }

    public Set<String> getSubscribersByTopic(String topic) {
        Set<String> result = topicToSessions.get(topic);
        return result != null ? Collections.unmodifiableSet(result) : Collections.emptySet();
    }
}

