package br.com.orleon.mq.probe.api.dto.messages;

import java.util.Map;

public record ReceivedMessageResponse(
        String messageId,
        String body,
        Map<String, Object> properties,
        Map<String, String> headers
) {
}
