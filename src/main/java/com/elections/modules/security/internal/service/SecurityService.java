package com.elections.modules.security.internal.service;

import com.elections.modules.security.SecurityApi;
import com.elections.modules.security.internal.dto.RateValidationDto;
import com.elections.modules.security.internal.repository.RateLimitRedisRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SecurityService implements SecurityApi {

    private final RateLimitRedisRepository rateLimitRedisRepository;

    public SecurityService(RateLimitRedisRepository rateLimitRedisRepository) {
        this.rateLimitRedisRepository = rateLimitRedisRepository;
    }

    @Override
    public RateValidationDto checkRateLimit(String clientIdentifier, int limitPerMinute) {
        Long requests = rateLimitRedisRepository.incrementCounter(clientIdentifier, Duration.ofMinutes(1));
        long current = requests != null ? requests : 1;
        boolean allowed = current <= limitPerMinute;
        long remaining = Math.max(0, limitPerMinute - current);
        return new RateValidationDto(allowed, remaining, 60);
    }
}

