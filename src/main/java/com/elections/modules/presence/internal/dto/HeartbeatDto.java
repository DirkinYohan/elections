package com.elections.modules.presence.internal.dto;

public class HeartbeatDto {
    private String auditorId;
    private String currentCaseId;
    private long timestamp;

    public HeartbeatDto() {}

    public HeartbeatDto(String auditorId, String currentCaseId) {
        this.auditorId = auditorId;
        this.currentCaseId = currentCaseId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getAuditorId() { return auditorId; }
    public void setAuditorId(String auditorId) { this.auditorId = auditorId; }

    public String getCurrentCaseId() { return currentCaseId; }
    public void setCurrentCaseId(String currentCaseId) { this.currentCaseId = currentCaseId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

