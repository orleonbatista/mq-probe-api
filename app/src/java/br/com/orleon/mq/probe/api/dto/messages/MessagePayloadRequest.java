package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MessagePayloadRequest(
        @NotBlank String body,
        @NotNull String format,
        Map<String, String> headers,
        Map<String, Object> properties
) {
}
