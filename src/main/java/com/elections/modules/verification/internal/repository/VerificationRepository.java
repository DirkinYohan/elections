package com.elections.modules.verification.internal.repository;

import com.elections.modules.verification.internal.model.VerificationCase;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class VerificationRepository {

    private final ConcurrentHashMap<String, VerificationCase> casesQueue = new ConcurrentHashMap<>();

    public void add(VerificationCase verificationCase) {
        casesQueue.put(verificationCase.getBallotId(), verificationCase);
    }

    public VerificationCase findById(String ballotId) {
        return casesQueue.get(ballotId);
    }

    public List<VerificationCase> getAvailableCases() {
        return new ArrayList<>(casesQueue.values());
    }

    public void remove(String ballotId) {
        casesQueue.remove(ballotId);
    }
}

