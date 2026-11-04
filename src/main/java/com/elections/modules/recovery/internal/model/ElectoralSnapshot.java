package com.elections.modules.recovery.internal.model;

public class ElectoralSnapshot {
    private String topic;
    private long monotonicId;
    private Object data;
    private long timestamp;

    public ElectoralSnapshot() {}

    public ElectoralSnapshot(String topic, long monotonicId, Object data) {
        this.topic = topic;
        this.monotonicId = monotonicId;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public long getMonotonicId() { return monotonicId; }
    public void setMonotonicId(long monotonicId) { this.monotonicId = monotonicId; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

