package br.com.orleon.mq.probe.domain.model.message;

import java.util.Optional;

public record QueueManagerCredentials(
        String username,
        String password
) {

    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    public Optional<String> password() {
        return Optional.ofNullable(password);
    }
}
