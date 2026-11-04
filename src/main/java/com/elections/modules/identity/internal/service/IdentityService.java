package com.elections.modules.identity.internal.service;

import com.elections.modules.identity.IdentityApi;
import com.elections.modules.identity.internal.dto.SessionDto;
import com.elections.modules.identity.internal.model.User;
import com.elections.modules.identity.internal.model.UserRole;
import com.elections.modules.identity.internal.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdentityService implements IdentityApi {

    private final UserRepository userRepository;
    private final Map<String, SessionDto> activeSessions = new ConcurrentHashMap<>();

    public IdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public SessionDto authenticate(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            User u = userOpt.get();
            String token = UUID.randomUUID().toString();
            long expiresAt = System.currentTimeMillis() + (8 * 3600 * 1000); // 8 hours
            SessionDto session = new SessionDto(token, u.getUsername(), u.getRole(), expiresAt);
            activeSessions.put(token, session);
            return session;
        }
        return null;
    }

    @Override
    public boolean validateToken(String token, UserRole requiredRole) {
        SessionDto s = activeSessions.get(token);
        if (s != null && s.getExpiresAt() > System.currentTimeMillis()) {
            return requiredRole == null || requiredRole == s.getRole();
        }
        return false;
    }

    @Override
    public Optional<String> getUsernameByToken(String token) {
        SessionDto s = activeSessions.get(token);
        return s != null ? Optional.of(s.getUsername()) : Optional.empty();
    }
}

