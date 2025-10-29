package br.com.orleon.mq.probe.api.dto.messages;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QueueManagerRequest(
        @NotBlank String name,
        @NotBlank String channel,
        @Valid @NotNull @Size(min = 1) List<EndpointRequest> endpoints,
        @Valid CredentialsRequest credentials,
        boolean useTls,
        String cipherSuite
) {
}
