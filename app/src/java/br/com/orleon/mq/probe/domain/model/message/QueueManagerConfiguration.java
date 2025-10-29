package br.com.orleon.mq.probe.domain.model.message;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record QueueManagerConfiguration(
        String name,
        String channel,
        List<QueueEndpoint> endpoints,
        QueueManagerCredentials credentials,
        boolean useTls,
        String cipherSuite
) {

    public QueueManagerConfiguration {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("At least one endpoint must be provided");
        }
        credentials = credentials == null ? new QueueManagerCredentials(null, null) : credentials;
        if (useTls && (cipherSuite == null || cipherSuite.isBlank())) {
            throw new IllegalArgumentException("cipherSuite must be provided when TLS is enabled");
        }
    }

    public List<QueueEndpoint> endpoints() {
        return Collections.unmodifiableList(endpoints);
    }
}
