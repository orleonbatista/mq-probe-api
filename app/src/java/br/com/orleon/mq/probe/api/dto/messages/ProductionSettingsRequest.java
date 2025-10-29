package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductionSettingsRequest(
        @Positive int totalMessages,
        @Positive int batchSize,
        @Positive int concurrency,
        @NotNull String deliveryDelay,
        @NotNull String timeToLive,
        boolean persistent
) {
}
