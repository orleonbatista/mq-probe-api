package br.com.orleon.mq.probe.api.controller;

import br.com.orleon.mq.probe.api.dto.messages.ConsumeMessageRequest;
import br.com.orleon.mq.probe.api.dto.messages.MessageOperationResponse;
import br.com.orleon.mq.probe.api.dto.messages.ProduceMessageRequest;
import br.com.orleon.mq.probe.api.mapper.MessageMapper;
import br.com.orleon.mq.probe.application.usecase.ConsumeMessageUseCase;
import br.com.orleon.mq.probe.application.usecase.ProduceMessageUseCase;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Messages")
@RestController
@RequestMapping("/messages")
public class MessageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageController.class);

    private final ProduceMessageUseCase produceMessageUseCase;
    private final ConsumeMessageUseCase consumeMessageUseCase;
    private final MessageMapper messageMapper;

    public MessageController(ProduceMessageUseCase produceMessageUseCase,
                             ConsumeMessageUseCase consumeMessageUseCase,
                             MessageMapper messageMapper) {
        this.produceMessageUseCase = produceMessageUseCase;
        this.consumeMessageUseCase = consumeMessageUseCase;
        this.messageMapper = messageMapper;
    }

    @Operation(summary = "Produce messages into IBM MQ queues with idempotency")
    @PostMapping("/produce")
    public ResponseEntity<MessageOperationResponse> produce(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
                                                            @RequestHeader(value = "Idempotency-Expiry-Seconds", required = false) Long idempotencyExpirySeconds,
                                                            @Valid @RequestBody ProduceMessageRequest request) {
        validateIdempotencyKey(idempotencyKeyHeader, request.idempotencyKey());
        Duration ttlOverride = resolveTtl(idempotencyExpirySeconds);
        LOGGER.info("Received produce request with idempotency key {}", request.idempotencyKey());
        MessageOperationResult result = produceMessageUseCase.execute(messageMapper.toCommand(request), ttlOverride);
        return ResponseEntity.ok(messageMapper.toResponse(result));
    }

    @Operation(summary = "Consume messages from IBM MQ queues with idempotency")
    @PostMapping("/consume")
    public ResponseEntity<MessageOperationResponse> consume(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
                                                            @RequestHeader(value = "Idempotency-Expiry-Seconds", required = false) Long idempotencyExpirySeconds,
                                                            @Valid @RequestBody ConsumeMessageRequest request) {
        validateIdempotencyKey(idempotencyKeyHeader, request.idempotencyKey());
        Duration ttlOverride = resolveTtl(idempotencyExpirySeconds);
        LOGGER.info("Received consume request with idempotency key {}", request.idempotencyKey());
        MessageOperationResult result = consumeMessageUseCase.execute(messageMapper.toCommand(request), ttlOverride);
        return ResponseEntity.ok(messageMapper.toResponse(result));
    }

    private void validateIdempotencyKey(String headerValue, String bodyValue) {
        if (StringUtils.hasText(headerValue) && !headerValue.equals(bodyValue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Header Idempotency-Key must match request payload");
        }
    }

    private Duration resolveTtl(Long expirySeconds) {
        if (expirySeconds == null || expirySeconds <= 0) {
            return null;
        }
        return Duration.ofSeconds(expirySeconds);
    }
}
