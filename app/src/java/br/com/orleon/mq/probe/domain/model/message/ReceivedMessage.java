package br.com.orleon.mq.probe.domain.model.message;

import java.util.Collections;
import java.util.Map;

public record ReceivedMessage(
        String messageId,
        String body,
        Map<String, Object> properties,
        Map<String, String> headers
) {

    public ReceivedMessage {
        properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
        headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
    }
}
