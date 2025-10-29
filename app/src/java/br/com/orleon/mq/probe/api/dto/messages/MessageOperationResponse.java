package br.com.orleon.mq.probe.api.dto.messages;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MessageOperationResponse(
        String idempotencyKey,
        String operationType,
        int requestedMessages,
        int processedMessages,
        Instant startedAt,
        Instant completedAt,
        Duration elapsed,
        Map<String, Object> metadata,
        List<ReceivedMessageResponse> messages
) {
}
