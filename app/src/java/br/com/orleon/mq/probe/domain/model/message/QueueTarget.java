package br.com.orleon.mq.probe.domain.model.message;

import java.util.Optional;

public record QueueTarget(
        String queueName,
        String replyToQueue
) {

    public QueueTarget {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("queueName must not be blank");
        }
    }

    public Optional<String> replyToQueueOptional() {
        return Optional.ofNullable(replyToQueue);
    }
}
