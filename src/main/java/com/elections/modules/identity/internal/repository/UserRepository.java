package com.elections.modules.identity.internal.repository;

import com.elections.modules.identity.internal.model.User;
import com.elections.modules.identity.internal.model.UserRole;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepository {

    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    public UserRepository() {
        users.put("jury1", new User("1", "jury1", "pass123", UserRole.JURY));
        users.put("auditor1", new User("2", "auditor1", "pass123", UserRole.AUDITOR));
        users.put("coordinator1", new User("3", "coordinator1", "pass123", UserRole.COORDINATOR));
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }
}

