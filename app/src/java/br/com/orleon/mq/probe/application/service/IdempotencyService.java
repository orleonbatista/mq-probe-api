package br.com.orleon.mq.probe.application.service;

import br.com.orleon.mq.probe.domain.exception.IdempotencyConflictException;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyRecord;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyStatus;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.ports.IdempotencyRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final Clock clock;
    private final Duration recordTtl;

    public IdempotencyService(IdempotencyRepository repository, Clock clock, Duration recordTtl) {
        this.repository = repository;
        this.clock = clock;
        this.recordTtl = recordTtl;
    }

    public Optional<IdempotencyRecord> find(MessageOperationType operationType, String idempotencyKey) {
        return repository.find(operationType, idempotencyKey);
    }

    public void acquireLock(MessageOperationType operationType,
                            String idempotencyKey,
                            String requestPayload) {
        acquireLock(operationType, idempotencyKey, requestPayload, null);
    }

    public void acquireLock(MessageOperationType operationType,
                            String idempotencyKey,
                            String requestPayload,
                            Duration overrideTtl) {
        String requestHash = hash(requestPayload);
        Instant now = clock.instant();
        Duration ttlToUse = overrideTtl != null ? overrideTtl : recordTtl;
        Instant expiresAt = now.plus(ttlToUse);

        Optional<IdempotencyRecord> existing = repository.find(operationType, idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.status() == IdempotencyStatus.IN_PROGRESS) {
                throw new IdempotencyConflictException("Operation already in progress for key " + idempotencyKey);
            }
            if (!requestHash.equals(record.requestHash())) {
                throw new IdempotencyConflictException("Idempotency key reused with different payload");
            }
            if (record.status() == IdempotencyStatus.COMPLETED) {
                return;
            }
        }

        boolean created = repository.createInProgress(operationType, idempotencyKey, requestHash, now, expiresAt);
        if (!created) {
            throw new IdempotencyConflictException("Failed to acquire idempotency lock for key " + idempotencyKey);
        }
    }

    public void markCompleted(MessageOperationType operationType,
                              String idempotencyKey,
                              String responsePayload) {
        repository.markCompleted(operationType, idempotencyKey, responsePayload, clock.instant());
    }

    public void markFailed(MessageOperationType operationType,
                           String idempotencyKey,
                           IdempotencyStatus status) {
        repository.markFailed(operationType, idempotencyKey, clock.instant(), status);
    }

    private String hash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
