package com.elections.modules.security;

import com.elections.modules.security.internal.dto.RateValidationDto;

/**
 * Public API for the Operational Security Module.
 * Enforces rate limiting per window and mitigates reconnection stampedes.
 */
public interface SecurityApi {

    RateValidationDto checkRateLimit(String clientIdentifier, int limitPerMinute);
}

