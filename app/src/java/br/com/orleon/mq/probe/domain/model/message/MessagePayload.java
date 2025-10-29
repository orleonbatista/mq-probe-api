package br.com.orleon.mq.probe.domain.model.message;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record MessagePayload(
        String body,
        MessagePayloadFormat format,
        Map<String, String> headers,
        Map<String, Object> properties
) {

    public MessagePayload {
        Objects.requireNonNull(format, "format must not be null");
        headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
        properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
    }

    public enum MessagePayloadFormat {
        TEXT,
        BINARY,
        JSON
    }
}
