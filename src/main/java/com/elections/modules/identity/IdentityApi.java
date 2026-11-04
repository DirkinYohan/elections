package com.elections.modules.identity;

import com.elections.modules.identity.internal.dto.SessionDto;
import com.elections.modules.identity.internal.model.UserRole;

import java.util.Optional;

/**
 * Public API for the Identity & Access Module.
 * Authenticates users and checks permissions based on roles.
 */
public interface IdentityApi {

    SessionDto authenticate(String username, String password);

    boolean validateToken(String token, UserRole requiredRole);

    Optional<String> getUsernameByToken(String token);
}

