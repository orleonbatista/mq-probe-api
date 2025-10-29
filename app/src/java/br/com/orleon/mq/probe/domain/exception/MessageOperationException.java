package br.com.orleon.mq.probe.domain.exception;

public class MessageOperationException extends RuntimeException {

    public MessageOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageOperationException(String message) {
        super(message);
    }
}
