package com.elections.modules.audit.internal.model;

public class AuditRecord {
    private String id;
    private String eventType;
    private String ballotId;
    private String responsibleUser;
    private String details;
    private long timestamp;

    public AuditRecord() {}

    public AuditRecord(String id, String eventType, String ballotId, String responsibleUser, String details) {
        this.id = id;
        this.eventType = eventType;
        this.ballotId = ballotId;
        this.responsibleUser = responsibleUser;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getBallotId() { return ballotId; }
    public void setBallotId(String ballotId) { this.ballotId = ballotId; }

    public String getResponsibleUser() { return responsibleUser; }
    public void setResponsibleUser(String responsibleUser) { this.responsibleUser = responsibleUser; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

