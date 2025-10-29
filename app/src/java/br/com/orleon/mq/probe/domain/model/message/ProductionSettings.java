package br.com.orleon.mq.probe.domain.model.message;

import java.time.Duration;

public record ProductionSettings(
        int totalMessages,
        int batchSize,
        int concurrency,
        Duration deliveryDelay,
        Duration timeToLive,
        boolean persistent
) {

    public ProductionSettings {
        if (totalMessages <= 0) {
            throw new IllegalArgumentException("totalMessages must be greater than zero");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be greater than zero");
        }
        deliveryDelay = deliveryDelay == null ? Duration.ZERO : deliveryDelay;
        timeToLive = timeToLive == null ? Duration.ZERO : timeToLive;
    }
}
