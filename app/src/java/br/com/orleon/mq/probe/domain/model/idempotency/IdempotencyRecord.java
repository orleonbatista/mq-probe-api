package br.com.orleon.mq.probe.domain.model.idempotency;

import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class IdempotencyRecord {

    private final MessageOperationType operationType;
    private final String idempotencyKey;
    private final String requestHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private IdempotencyStatus status;
    private Instant lastUpdatedAt;
    private String responsePayload;

    public IdempotencyRecord(MessageOperationType operationType,
                              String idempotencyKey,
                              String requestHash,
                              IdempotencyStatus status,
                              Instant createdAt,
                              Instant expiresAt,
                              Instant lastUpdatedAt,
                              String responsePayload) {
        this.operationType = Objects.requireNonNull(operationType, "operationType");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = requestHash;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.lastUpdatedAt = lastUpdatedAt == null ? createdAt : lastUpdatedAt;
        this.responsePayload = responsePayload;
    }

    public MessageOperationType operationType() {
        return operationType;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    public IdempotencyStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Optional<String> responsePayload() {
        return Optional.ofNullable(responsePayload);
    }

    public void markCompleted(String responsePayload, Instant updatedAt) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responsePayload = responsePayload;
        this.lastUpdatedAt = updatedAt;
    }

    public void markFailed(Instant updatedAt) {
        this.status = IdempotencyStatus.FAILED;
        this.lastUpdatedAt = updatedAt;
    }
}
