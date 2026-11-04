package com.elections.modules.verification.internal.dto;

import java.util.Map;

public class CaseResolutionDto {
    private String ballotId;
    private String auditorId;
    private String decision; // CORRECTED, REJECTED, RATIFIED
    private Map<String, Integer> correctedVotes;
    private String notes;

    public CaseResolutionDto() {}

    public String getBallotId() { return ballotId; }
    public void setBallotId(String ballotId) { this.ballotId = ballotId; }

    public String getAuditorId() { return auditorId; }
    public void setAuditorId(String auditorId) { this.auditorId = auditorId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public Map<String, Integer> getCorrectedVotes() { return correctedVotes; }
    public void setCorrectedVotes(Map<String, Integer> correctedVotes) { this.correctedVotes = correctedVotes; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

