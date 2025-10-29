package br.com.orleon.mq.probe.domain.model.message;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MessageOperationResult(
        String idempotencyKey,
        MessageOperationType operationType,
        int requestedMessages,
        int processedMessages,
        Instant startedAt,
        Instant completedAt,
        Duration elapsed,
        Map<String, Object> metadata,
        List<ReceivedMessage> receivedMessages
) {
}
