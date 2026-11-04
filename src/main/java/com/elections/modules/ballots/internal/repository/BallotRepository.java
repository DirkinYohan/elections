package com.elections.modules.ballots.internal.repository;

import com.elections.modules.ballots.internal.model.Ballot;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class BallotRepository {

    private final ConcurrentHashMap<String, Ballot> storage = new ConcurrentHashMap<>();

    public void save(Ballot ballot) {
        storage.put(ballot.getId(), ballot);
    }

    public Optional<Ballot> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    public boolean exists(String id) {
        return storage.containsKey(id);
    }
}

