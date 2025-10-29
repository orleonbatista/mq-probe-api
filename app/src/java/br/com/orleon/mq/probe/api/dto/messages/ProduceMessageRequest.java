package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProduceMessageRequest(
        @NotBlank String idempotencyKey,
        @Valid @NotNull QueueManagerRequest queueManager,
        @Valid @NotNull QueueTargetRequest target,
        @Valid @NotNull ProductionSettingsRequest settings,
        @Valid @NotNull @Size(min = 1) List<MessagePayloadRequest> messages
) {
}
