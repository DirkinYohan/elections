package com.elections.modules.identity.internal.dto;

import com.elections.modules.identity.internal.model.UserRole;

public class SessionDto {
    private String token;
    private String username;
    private UserRole role;
    private long expiresAt;

    public SessionDto() {}

    public SessionDto(String token, String username, UserRole role, long expiresAt) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}

