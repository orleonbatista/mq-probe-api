package br.com.orleon.mq.probe.domain.model.message;

import java.util.Objects;

public record QueueEndpoint(
        String host,
        int port
) {

    public QueueEndpoint {
        Objects.requireNonNull(host, "host must not be null");
    }
}
