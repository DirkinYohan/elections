package com.elections.modules.presence.internal.model;

public class AuditorPresence {
    private String auditorId;
    private String currentCaseId;
    private long lastHeartbeat;
    private boolean active;

    public AuditorPresence() {}

    public AuditorPresence(String auditorId, String currentCaseId) {
        this.auditorId = auditorId;
        this.currentCaseId = currentCaseId;
        this.lastHeartbeat = System.currentTimeMillis();
        this.active = true;
    }

    public String getAuditorId() { return auditorId; }
    public void setAuditorId(String auditorId) { this.auditorId = auditorId; }

    public String getCurrentCaseId() { return currentCaseId; }
    public void setCurrentCaseId(String currentCaseId) { this.currentCaseId = currentCaseId; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

