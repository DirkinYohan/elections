package com.elections.modules.results.internal.dto;

import com.elections.modules.results.internal.model.AggregationLevel;

import java.util.Map;

public class AggregateDto {

    private AggregationLevel level;
    private String identifier;
    private int reportedTables;
    private int totalTables;
    private double reportedPercentage;
    private Map<String, Integer> candidateTotals;

    public AggregateDto() {}

    public AggregationLevel getLevel() { return level; }
    public void setLevel(AggregationLevel level) { this.level = level; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public int getReportedTables() { return reportedTables; }
    public void setReportedTables(int reportedTables) { this.reportedTables = reportedTables; }

    public int getTotalTables() { return totalTables; }
    public void setTotalTables(int totalTables) { this.totalTables = totalTables; }

    public double getReportedPercentage() { return reportedPercentage; }
    public void setReportedPercentage(double reportedPercentage) { this.reportedPercentage = reportedPercentage; }

    public Map<String, Integer> getCandidateTotals() { return candidateTotals; }
    public void setCandidateTotals(Map<String, Integer> candidateTotals) { this.candidateTotals = candidateTotals; }
}

