package com.elections.modules.security.internal.dto;

public class RateValidationDto {
    private boolean allowed;
    private long remainingRequests;
    private long secondsUntilReset;

    public RateValidationDto() {}

    public RateValidationDto(boolean allowed, long remainingRequests, long secondsUntilReset) {
        this.allowed = allowed;
        this.remainingRequests = remainingRequests;
        this.secondsUntilReset = secondsUntilReset;
    }

    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }

    public long getRemainingRequests() { return remainingRequests; }
    public void setRemainingRequests(long remainingRequests) { this.remainingRequests = remainingRequests; }

    public long getSecondsUntilReset() { return secondsUntilReset; }
    public void setSecondsUntilReset(long secondsUntilReset) { this.secondsUntilReset = secondsUntilReset; }
}

