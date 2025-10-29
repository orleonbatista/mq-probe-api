package br.com.orleon.mq.probe.api.dto.messages;

public record CredentialsRequest(
        String username,
        String password
) {
}
