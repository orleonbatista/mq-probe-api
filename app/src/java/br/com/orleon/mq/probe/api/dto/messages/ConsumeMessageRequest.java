package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsumeMessageRequest(
        @NotBlank String idempotencyKey,
        @Valid @NotNull QueueManagerRequest queueManager,
        @Valid @NotNull QueueTargetRequest target,
        @Valid @NotNull ConsumptionSettingsRequest settings
) {
}
