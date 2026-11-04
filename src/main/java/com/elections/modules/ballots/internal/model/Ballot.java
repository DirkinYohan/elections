package com.elections.modules.ballots.internal.model;

import java.util.Map;

public class Ballot {
    private String id;
    private Integer pollingTable;
    private Integer pollingStation;
    private Integer municipality;
    private Integer version;
    private String pollWorkerId;
    private Long closedAtTimestamp;
    private Map<String, Integer> votes;
    private Integer totalVoters;
    private String photoHash;
    private BallotStatus status;

    public Ballot() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getPollingTable() { return pollingTable; }
    public void setPollingTable(Integer pollingTable) { this.pollingTable = pollingTable; }

    public Integer getPollingStation() { return pollingStation; }
    public void setPollingStation(Integer pollingStation) { this.pollingStation = pollingStation; }

    public Integer getMunicipality() { return municipality; }
    public void setMunicipality(Integer municipality) { this.municipality = municipality; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getPollWorkerId() { return pollWorkerId; }
    public void setPollWorkerId(String pollWorkerId) { this.pollWorkerId = pollWorkerId; }

    public Long getClosedAtTimestamp() { return closedAtTimestamp; }
    public void setClosedAtTimestamp(Long closedAtTimestamp) { this.closedAtTimestamp = closedAtTimestamp; }

    public Map<String, Integer> getVotes() { return votes; }
    public void setVotes(Map<String, Integer> votes) { this.votes = votes; }

    public Integer getTotalVoters() { return totalVoters; }
    public void setTotalVoters(Integer totalVoters) { this.totalVoters = totalVoters; }

    public String getPhotoHash() { return photoHash; }
    public void setPhotoHash(String photoHash) { this.photoHash = photoHash; }

    public BallotStatus getStatus() { return status; }
    public void setStatus(BallotStatus status) { this.status = status; }
}

