package br.com.orleon.mq.probe.domain.ports;

import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyRecord;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyStatus;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRepository {

    Optional<IdempotencyRecord> find(MessageOperationType operationType, String idempotencyKey);

    boolean createInProgress(MessageOperationType operationType,
                              String idempotencyKey,
                              String requestHash,
                              Instant createdAt,
                              Instant expiresAt);

    void markCompleted(MessageOperationType operationType,
                       String idempotencyKey,
                       String responsePayload,
                       Instant updatedAt);

    void markFailed(MessageOperationType operationType,
                    String idempotencyKey,
                    Instant updatedAt,
                    IdempotencyStatus status);
}
