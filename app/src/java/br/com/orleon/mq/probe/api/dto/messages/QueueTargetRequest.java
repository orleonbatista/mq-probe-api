package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.constraints.NotBlank;

public record QueueTargetRequest(
        @NotBlank String queueName,
        String replyToQueue
) {
}
