package com.elections.modules.verification.internal.model;

public class VerificationCase {
    private String ballotId;
    private int priority;
    private String assignedAuditorId;
    private Long lockExpiresAt;
    private CaseStatus status;

    public VerificationCase() {}

    public VerificationCase(String ballotId, int priority) {
        this.ballotId = ballotId;
        this.priority = priority;
        this.status = CaseStatus.QUEUED;
    }

    public String getBallotId() { return ballotId; }
    public void setBallotId(String ballotId) { this.ballotId = ballotId; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getAssignedAuditorId() { return assignedAuditorId; }
    public void setAssignedAuditorId(String assignedAuditorId) { this.assignedAuditorId = assignedAuditorId; }

    public Long getLockExpiresAt() { return lockExpiresAt; }
    public void setLockExpiresAt(Long lockExpiresAt) { this.lockExpiresAt = lockExpiresAt; }

    public CaseStatus getStatus() { return status; }
    public void setStatus(CaseStatus status) { this.status = status; }
}

