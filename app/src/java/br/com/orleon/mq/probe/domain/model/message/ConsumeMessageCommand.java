package br.com.orleon.mq.probe.domain.model.message;

public record ConsumeMessageCommand(
        String idempotencyKey,
        QueueManagerConfiguration queueManager,
        QueueTarget target,
        ConsumptionSettings settings
) {
}
