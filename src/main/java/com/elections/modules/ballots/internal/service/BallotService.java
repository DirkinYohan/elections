package com.elections.modules.ballots.internal.service;

import com.elections.infrastructure.events.DomainEventPublisher;
import com.elections.modules.ballots.BallotsApi;
import com.elections.modules.ballots.internal.dto.BallotTransmissionDto;
import com.elections.modules.ballots.internal.model.Ballot;
import com.elections.modules.ballots.internal.model.BallotStatus;
import com.elections.modules.ballots.internal.repository.BallotRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class BallotService implements BallotsApi {

    private final BallotRepository ballotRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final DomainEventPublisher domainEventPublisher;

    public BallotService(BallotRepository ballotRepository,
                         StringRedisTemplate stringRedisTemplate,
                         DomainEventPublisher domainEventPublisher) {
        this.ballotRepository = ballotRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public Map<String, Object> processTransmission(BallotTransmissionDto dto) {
        Map<String, Object> response = new HashMap<>();

        // RF-1 Idempotency check with Redis
        String idempotencyKey = "idempotency:ballot:" + dto.getIdempotencyKey();
        Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(idempotencyKey, dto.getBallotId(), Duration.ofHours(4));
        if (Boolean.FALSE.equals(isNew)) {
            response.put("status", "DUPLICATE");
            response.put("message", "Ballot previously processed with idempotency key");
            response.put("ballotId", dto.getBallotId());
            return response;
        }

        // Accounting checksum: sum of votes + blanks + nulls + unmarked == total voters
        int totalVotes = dto.getVotes() != null ? dto.getVotes().values().stream().mapToInt(Integer::intValue).sum() : 0;
        boolean isValid = (totalVotes == (dto.getTotalVoters() != null ? dto.getTotalVoters() : -1));

        Ballot ballot = new Ballot();
        ballot.setId(dto.getBallotId());
        ballot.setPollingTable(dto.getPollingTable());
        ballot.setPollingStation(dto.getPollingStation());
        ballot.setMunicipality(dto.getMunicipality());
        ballot.setVersion(dto.getVersion());
        ballot.setPollWorkerId(dto.getPollWorkerId());
        ballot.setClosedAtTimestamp(dto.getClosedAtTimestamp());
        ballot.setVotes(dto.getVotes());
        ballot.setTotalVoters(dto.getTotalVoters());
        ballot.setPhotoHash(dto.getPhotoHash());
        ballot.setStatus(isValid ? BallotStatus.ACCEPTED : BallotStatus.SUSPICIOUS);

        ballotRepository.save(ballot);

        response.put("status", isValid ? "ACCEPTED" : "SUSPICIOUS_FOR_VERIFICATION");
        response.put("ballotId", ballot.getId());
        response.put("version", ballot.getVersion());

        return response;
    }

    @Override
    public boolean existsBallot(String ballotId) {
        return ballotRepository.exists(ballotId);
    }
}

