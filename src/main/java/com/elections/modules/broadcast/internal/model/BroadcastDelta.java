package com.elections.modules.broadcast.internal.model;

import java.util.Map;

public class BroadcastDelta {
    private String topic;
    private Map<String, Integer> deltaVotes;
    private int deltaTables;

    public BroadcastDelta() {}

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Map<String, Integer> getDeltaVotes() { return deltaVotes; }
    public void setDeltaVotes(Map<String, Integer> deltaVotes) { this.deltaVotes = deltaVotes; }

    public int getDeltaTables() { return deltaTables; }
    public void setDeltaTables(int deltaTables) { this.deltaTables = deltaTables; }
}

