package com.elections.modules.recovery.internal.dto;

import java.util.List;

public class StateRecoveryDto {
    private String type; // "SNAPSHOT" or "DELTAS"
    private long lastMonotonicId;
    private Object snapshot;
    private List<Object> deltas;

    public StateRecoveryDto() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getLastMonotonicId() { return lastMonotonicId; }
    public void setLastMonotonicId(long lastMonotonicId) { this.lastMonotonicId = lastMonotonicId; }

    public Object getSnapshot() { return snapshot; }
    public void setSnapshot(Object snapshot) { this.snapshot = snapshot; }

    public List<Object> getDeltas() { return deltas; }
    public void setDeltas(List<Object> deltas) { this.deltas = deltas; }
}

