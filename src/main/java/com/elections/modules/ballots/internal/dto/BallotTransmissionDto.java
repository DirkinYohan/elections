package com.elections.modules.ballots.internal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO representing an incoming E-14 ballot transmission message (Section 3.1).
 */
public class BallotTransmissionDto {

    @JsonProperty("acta")
    private String ballotId;          // e.g. E14-176-0042-03

    @JsonProperty("mesa")
    private Integer pollingTable;      // e.g. 4201

    @JsonProperty("puesto")
    private Integer pollingStation;    // e.g. 176

    @JsonProperty("municipio")
    private Integer municipality;      // e.g. 11

    @JsonProperty("version")
    private Integer version;           // 1, 2, 3...

    @JsonProperty("jurado")
    private String pollWorkerId;       // e.g. JUR-88134

    @JsonProperty("ts_cierre")
    private Long closedAtTimestamp;    // epoch timestamp

    @JsonProperty("votos")
    private Map<String, Integer> votes; // C1, C2, BLANCO, NULO, NO_MARCADO

    @JsonProperty("sufragantes")
    private Integer totalVoters;

    @JsonProperty("hash_foto")
    private String photoHash;

    @JsonProperty("idem")
    private String idempotencyKey;

    public BallotTransmissionDto() {}

    public String getBallotId() { return ballotId; }
    public void setBallotId(String ballotId) { this.ballotId = ballotId; }

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

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}

