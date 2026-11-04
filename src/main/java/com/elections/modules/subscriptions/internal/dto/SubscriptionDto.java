package com.elections.modules.subscriptions.internal.dto;

public class SubscriptionDto {
    private String sessionId;
    private String topic;

    public SubscriptionDto() {}

    public SubscriptionDto(String sessionId, String topic) {
        this.sessionId = sessionId;
        this.topic = topic;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
}

