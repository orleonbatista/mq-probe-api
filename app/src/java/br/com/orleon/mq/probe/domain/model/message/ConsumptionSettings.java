package br.com.orleon.mq.probe.domain.model.message;

import java.time.Duration;

public record ConsumptionSettings(
        int maxMessages,
        Duration waitTimeout,
        boolean autoAcknowledge
) {

    public ConsumptionSettings {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be greater than zero");
        }
        waitTimeout = waitTimeout == null ? Duration.ofSeconds(5) : waitTimeout;
    }
}
