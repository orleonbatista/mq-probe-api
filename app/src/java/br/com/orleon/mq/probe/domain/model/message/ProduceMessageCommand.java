package br.com.orleon.mq.probe.domain.model.message;

import java.util.List;

public record ProduceMessageCommand(
        String idempotencyKey,
        QueueManagerConfiguration queueManager,
        QueueTarget target,
        List<MessagePayload> payloads,
        ProductionSettings settings
) {
}
