package br.com.orleon.mq.probe.domain.model.idempotency;

public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
