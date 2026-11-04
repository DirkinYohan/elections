package com.elections.modules.results.internal.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ElectoralAggregate {
    private AggregationLevel level;
    private String code;
    private int reportedTables;
    private int totalTables;
    private Map<String, Integer> votes = new ConcurrentHashMap<>();

    public ElectoralAggregate() {}

    public ElectoralAggregate(AggregationLevel level, String code, int totalTables) {
        this.level = level;
        this.code = code;
        this.totalTables = totalTables;
    }

    public AggregationLevel getLevel() { return level; }
    public void setLevel(AggregationLevel level) { this.level = level; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public int getReportedTables() { return reportedTables; }
    public void setReportedTables(int reportedTables) { this.reportedTables = reportedTables; }

    public int getTotalTables() { return totalTables; }
    public void setTotalTables(int totalTables) { this.totalTables = totalTables; }

    public Map<String, Integer> getVotes() { return votes; }
    public void setVotes(Map<String, Integer> votes) { this.votes = votes; }
}

