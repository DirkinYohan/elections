package com.elections.modules.security.internal.model;

public class RateRule {
    private String clientType;
    private int maxRequestsPerWindow;
    private int windowSeconds;

    public RateRule() {}

    public RateRule(String clientType, int maxRequestsPerWindow, int windowSeconds) {
        this.clientType = clientType;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSeconds = windowSeconds;
    }

    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }

    public int getMaxRequestsPerWindow() { return maxRequestsPerWindow; }
    public void setMaxRequestsPerWindow(int maxRequestsPerWindow) { this.maxRequestsPerWindow = maxRequestsPerWindow; }

    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
}

