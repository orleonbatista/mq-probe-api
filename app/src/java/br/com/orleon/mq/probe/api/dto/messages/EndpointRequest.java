package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EndpointRequest(
        @NotBlank String host,
        @NotNull @Positive Integer port
) {
}
