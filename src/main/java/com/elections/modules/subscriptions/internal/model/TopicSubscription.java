package com.elections.modules.subscriptions.internal.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TopicSubscription {
    private String topic;
    private Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    public TopicSubscription() {}

    public TopicSubscription(String topic) {
        this.topic = topic;
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Set<String> getSessionIds() { return sessionIds; }
}

