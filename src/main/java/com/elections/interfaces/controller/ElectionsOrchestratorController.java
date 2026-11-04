package com.elections.interfaces.controller;

import com.elections.modules.audit.AuditApi;
import com.elections.modules.audit.internal.dto.AuditEventDto;
import com.elections.modules.ballots.BallotsApi;
import com.elections.modules.ballots.internal.dto.BallotTransmissionDto;
import com.elections.modules.broadcast.BroadcastApi;
import com.elections.modules.identity.IdentityApi;
import com.elections.modules.identity.internal.dto.SessionDto;
import com.elections.modules.presence.PresenceApi;
import com.elections.modules.presence.internal.dto.HeartbeatDto;
import com.elections.modules.recovery.RecoveryApi;
import com.elections.modules.recovery.internal.dto.StateRecoveryDto;
import com.elections.modules.results.ResultsApi;
import com.elections.modules.results.internal.dto.AggregateDto;
import com.elections.modules.security.SecurityApi;
import com.elections.modules.security.internal.dto.RateValidationDto;
import com.elections.modules.subscriptions.SubscriptionsApi;
import com.elections.modules.verification.VerificationApi;
import com.elections.modules.verification.internal.dto.CaseResolutionDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * APPLICATION ORCHESTRATOR CONTROLLER.
 * The ONLY controller in the application that exposes HTTP/REST endpoints to the Frontend.
 * Enforces separation of concerns, high cohesion, and low coupling by delegating
 * solely to the public APIs of the decoupled business modules.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ElectionsOrchestratorController {

    private final BallotsApi ballotsApi;
    private final ResultsApi resultsApi;
    private final VerificationApi verificationApi;
    private final BroadcastApi broadcastApi;
    private final SubscriptionsApi subscriptionsApi;
    private final RecoveryApi recoveryApi;
    private final PresenceApi presenceApi;
    private final IdentityApi identityApi;
    private final SecurityApi securityApi;
    private final AuditApi auditApi;

    public ElectionsOrchestratorController(BallotsApi ballotsApi,
                                          ResultsApi resultsApi,
                                          VerificationApi verificationApi,
                                          BroadcastApi broadcastApi,
                                          SubscriptionsApi subscriptionsApi,
                                          RecoveryApi recoveryApi,
                                          PresenceApi presenceApi,
                                          IdentityApi identityApi,
                                          SecurityApi securityApi,
                                          AuditApi auditApi) {
        this.ballotsApi = ballotsApi;
        this.resultsApi = resultsApi;
        this.verificationApi = verificationApi;
        this.broadcastApi = broadcastApi;
        this.subscriptionsApi = subscriptionsApi;
        this.recoveryApi = recoveryApi;
        this.presenceApi = presenceApi;
        this.identityApi = identityApi;
        this.securityApi = securityApi;
        this.auditApi = auditApi;
    }

    // ==========================================
    // 1. IDENTITY & ACCESS (AUTHENTICATION)
    // ==========================================
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        SessionDto session = identityApi.authenticate(username, password);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }
        return ResponseEntity.ok(session);
    }

    // ==========================================
    // 2. BALLOT TRANSMISSION (POLL WORKERS / JURIES)
    // ==========================================
    @PostMapping("/ballots")
    public ResponseEntity<?> receiveBallot(@RequestBody BallotTransmissionDto dto,
                                           @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous-jury") String clientId) {
        // Rate limiting check (RF-10)
        RateValidationDto rateLimit = securityApi.checkRateLimit(clientId, 120);
        if (!rateLimit.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded"));
        }

        Map<String, Object> result = ballotsApi.processTransmission(dto);

        // Immutable audit trail
        auditApi.recordEvent(new AuditEventDto(
                "BALLOT_RECEIVED",
                dto.getBallotId(),
                dto.getPollWorkerId(),
                "Result status: " + result.get("status")
        ));

        // If accepted, enqueue into broadcast pipeline
        if ("ACCEPTED".equals(result.get("status"))) {
            broadcastApi.queueUpdate("dept:global", dto);
        }

        return ResponseEntity.ok(result);
    }

    // ==========================================
    // 3. ELECTORAL RESULTS (PUBLIC AUDIENCE)
    // ==========================================
    @GetMapping("/results/department")
    public ResponseEntity<AggregateDto> getDepartmentResults() {
        return ResponseEntity.ok(resultsApi.getDepartmentResults());
    }

    @GetMapping("/results/municipality/{id}")
    public ResponseEntity<AggregateDto> getMunicipalityResults(@PathVariable Integer id) {
        return ResponseEntity.ok(resultsApi.getMunicipalityResults(id));
    }

    @GetMapping("/results/station/{id}")
    public ResponseEntity<AggregateDto> getPollingStationResults(@PathVariable Integer id) {
        return ResponseEntity.ok(resultsApi.getPollingStationResults(id));
    }

    // ==========================================
    // 4. CONTINUITY & RECOVERY (PUBLIC CLIENT RECONNECTION)
    // ==========================================
    @GetMapping("/results/recover")
    public ResponseEntity<StateRecoveryDto> recoverState(
            @RequestParam(defaultValue = "global") String topic,
            @RequestParam(defaultValue = "0") long lastMonotonicId) {
        return ResponseEntity.ok(recoveryApi.recoverFrom(topic, lastMonotonicId));
    }

    // ==========================================
    // 5. VERIFICATION QUEUE (AUDITORS)
    // ==========================================
    @GetMapping("/verification/queue")
    public ResponseEntity<List<Map<String, Object>>> getVerificationQueue() {
        return ResponseEntity.ok(verificationApi.listSuspiciousCasesQueue());
    }

    @PostMapping("/verification/lock")
    public ResponseEntity<?> acquireCaseLock(@RequestParam String ballotId, @RequestParam String auditorId) {
        boolean acquired = verificationApi.acquireCaseExclusiveLock(ballotId, auditorId);
        if (!acquired) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Case is already locked by another auditor or expired"));
        }
        auditApi.recordEvent(new AuditEventDto("CASE_LOCKED", ballotId, auditorId, "Case acquired for review"));
        return ResponseEntity.ok(Map.of("message", "Case locked successfully"));
    }

    @PostMapping("/verification/release")
    public ResponseEntity<?> releaseCaseLock(@RequestParam String ballotId, @RequestParam String auditorId) {
        boolean released = verificationApi.releaseCaseLock(ballotId, auditorId);
        if (!released) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not the lock owner or lock already expired"));
        }
        auditApi.recordEvent(new AuditEventDto("CASE_RELEASED", ballotId, auditorId, "Case lock released"));
        return ResponseEntity.ok(Map.of("message", "Case released successfully"));
    }

    @PostMapping("/verification/resolve")
    public ResponseEntity<?> resolveCase(@RequestBody CaseResolutionDto resolution) {
        boolean resolved = verificationApi.resolveCase(resolution);
        if (!resolved) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unable to resolve case. Verify lock assignment"));
        }
        auditApi.recordEvent(new AuditEventDto(
                "CASE_RESOLVED",
                resolution.getBallotId(),
                resolution.getAuditorId(),
                "Decision: " + resolution.getDecision()
        ));
        return ResponseEntity.ok(Map.of("message", "Case resolved successfully"));
    }

    // ==========================================
    // 6. PRESENCE (AUDITORS & COORDINATOR)
    // ==========================================
    @PostMapping("/verification/heartbeat")
    public ResponseEntity<?> recordHeartbeat(@RequestBody HeartbeatDto heartbeat) {
        presenceApi.registerHeartbeat(heartbeat);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/verification/auditors")
    public ResponseEntity<List<Map<String, Object>>> getActiveAuditors() {
        return ResponseEntity.ok(presenceApi.listActiveAuditors());
    }

    // ==========================================
    // 7. AUDIT & TRACEABILITY
    // ==========================================
    @GetMapping("/audit/{ballotId}")
    public ResponseEntity<List<AuditEventDto>> getBallotAuditTrail(@PathVariable String ballotId) {
        return ResponseEntity.ok(auditApi.getBallotTraceability(ballotId));
    }
}

