package br.com.orleon.mq.probe.application.usecase;

import br.com.orleon.mq.probe.application.service.IdempotencyService;
import br.com.orleon.mq.probe.domain.exception.IdempotencyConflictException;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyRecord;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyStatus;
import br.com.orleon.mq.probe.domain.model.message.ConsumeMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.ports.MessageConsumerPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

public class ConsumeMessageUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumeMessageUseCase.class);

    private final MessageConsumerPort consumerPort;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public ConsumeMessageUseCase(MessageConsumerPort consumerPort,
                                 IdempotencyService idempotencyService,
                                 ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry,
                                 ObservationRegistry observationRegistry) {
        this.consumerPort = consumerPort;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public MessageOperationResult execute(ConsumeMessageCommand command) {
        return execute(command, null);
    }

    public MessageOperationResult execute(ConsumeMessageCommand command, Duration ttlOverride) {
        String serializedCommand = serialize(command);
        Optional<IdempotencyRecord> cached = idempotencyService.find(MessageOperationType.CONSUME, command.idempotencyKey());
        if (cached.filter(record -> record.status() == IdempotencyStatus.COMPLETED).isPresent()) {
            IdempotencyRecord record = cached.get();
            LOGGER.info("Reusing cached consume result for idempotency key {}", command.idempotencyKey());
            return deserializeResult(record.responsePayload().orElseThrow(() ->
                    new IdempotencyConflictException("Idempotency record missing response payload")));
        }

        idempotencyService.acquireLock(MessageOperationType.CONSUME, command.idempotencyKey(), serializedCommand, ttlOverride);

        Observation observation = Observation.createNotStarted("messages.consume", observationRegistry)
                .contextualName("consume-messages")
                .lowCardinalityKeyValue("queue", command.target().queueName())
                .lowCardinalityKeyValue("queueManager", command.queueManager().name());
        Timer.Sample sample = Timer.start(meterRegistry);

        try (Observation.Scope scope = observation.openScope()) {
            MessageOperationResult result = consumerPort.consume(command);
            observation.highCardinalityKeyValue("messages.processed", String.valueOf(result.processedMessages()));
            sample.stop(meterRegistry.timer("mq.probe.messages.consumed",
                    "queue", command.target().queueName(),
                    "queueManager", command.queueManager().name()));
            idempotencyService.markCompleted(MessageOperationType.CONSUME,
                    command.idempotencyKey(), serialize(result));
            return result;
        } catch (RuntimeException ex) {
            observation.error(ex);
            idempotencyService.markFailed(MessageOperationType.CONSUME,
                    command.idempotencyKey(), IdempotencyStatus.FAILED);
            throw ex;
        } finally {
            observation.stop();
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payload for idempotency", e);
        }
    }

    private MessageOperationResult deserializeResult(String json) {
        try {
            return objectMapper.readValue(json, MessageOperationResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotent response", e);
        }
    }
}
