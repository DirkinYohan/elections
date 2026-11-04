package com.elections.modules.verification.internal.service;

import com.elections.modules.verification.VerificationApi;
import com.elections.modules.verification.internal.dto.CaseResolutionDto;
import com.elections.modules.verification.internal.model.CaseStatus;
import com.elections.modules.verification.internal.model.VerificationCase;
import com.elections.modules.verification.internal.repository.VerificationRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class VerificationService implements VerificationApi {

    private final VerificationRepository verificationRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public VerificationService(VerificationRepository verificationRepository, StringRedisTemplate stringRedisTemplate) {
        this.verificationRepository = verificationRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<Map<String, Object>> listSuspiciousCasesQueue() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (VerificationCase c : verificationRepository.getAvailableCases()) {
            Map<String, Object> map = new HashMap<>();
            map.put("ballotId", c.getBallotId());
            map.put("priority", c.getPriority());
            map.put("status", c.getStatus().name());
            map.put("assignedAuditorId", c.getAssignedAuditorId());
            list.add(map);
        }
        return list;
    }

    @Override
    public boolean acquireCaseExclusiveLock(String ballotId, String auditorId) {
        // Secure distributed lock in Redis with owner identifier and TTL (RF-7)
        String lockKey = "lock:verification:" + ballotId;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, auditorId, Duration.ofMinutes(5));
        if (Boolean.TRUE.equals(acquired)) {
            VerificationCase c = verificationRepository.findById(ballotId);
            if (c != null) {
                c.setAssignedAuditorId(auditorId);
                c.setStatus(CaseStatus.LOCKED);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean releaseCaseLock(String ballotId, String auditorId) {
        // Safe release verifying owner token to prevent releasing another auditor's reassigned lock
        String lockKey = "lock:verification:" + ballotId;
        String currentOwner = stringRedisTemplate.opsForValue().get(lockKey);
        if (auditorId.equals(currentOwner)) {
            stringRedisTemplate.delete(lockKey);
            VerificationCase c = verificationRepository.findById(ballotId);
            if (c != null) {
                c.setAssignedAuditorId(null);
                c.setStatus(CaseStatus.QUEUED);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean resolveCase(CaseResolutionDto resolution) {
        if (releaseCaseLock(resolution.getBallotId(), resolution.getAuditorId())) {
            verificationRepository.remove(resolution.getBallotId());
            return true;
        }
        return false;
    }
}

