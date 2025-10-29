package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConsumptionSettingsRequest(
        @Positive int maxMessages,
        @NotNull String waitTimeout,
        boolean autoAcknowledge
) {
}
