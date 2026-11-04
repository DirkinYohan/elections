package com.elections.modules.broadcast.internal.dto;

public class BroadcastMessageDto {
    private String topic;
    private long monotonicId;
    private Object payload;
    private long timestamp;

    public BroadcastMessageDto() {}

    public BroadcastMessageDto(String topic, long monotonicId, Object payload) {
        this.topic = topic;
        this.monotonicId = monotonicId;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public long getMonotonicId() { return monotonicId; }
    public void setMonotonicId(long monotonicId) { this.monotonicId = monotonicId; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

