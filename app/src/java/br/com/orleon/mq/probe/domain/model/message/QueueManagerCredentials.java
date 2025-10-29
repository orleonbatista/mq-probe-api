package br.com.orleon.mq.probe.domain.model.message;

import java.util.Optional;

public record QueueManagerCredentials(
        String username,
        String password
) {

    public Optional<String> usernameOptional() {
        return Optional.ofNullable(username);
    }

    public Optional<String> passwordOptional() {
        return Optional.ofNullable(password);
    }
}
