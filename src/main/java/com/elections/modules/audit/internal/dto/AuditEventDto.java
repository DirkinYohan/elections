package com.elections.modules.audit.internal.dto;

public class AuditEventDto {
    private String eventType; // BALLOT_RECEIVED, BALLOT_CORRECTED, CASE_LOCKED, CASE_RESOLVED
    private String ballotId;
    private String responsibleUser;
    private String details;
    private long timestamp;

    public AuditEventDto() {}

    public AuditEventDto(String eventType, String ballotId, String responsibleUser, String details) {
        this.eventType = eventType;
        this.ballotId = ballotId;
        this.responsibleUser = responsibleUser;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }

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

