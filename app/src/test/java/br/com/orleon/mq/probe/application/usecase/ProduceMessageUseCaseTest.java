package br.com.orleon.mq.probe.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.orleon.mq.probe.application.service.IdempotencyService;
import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyRecord;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyStatus;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.MessagePayload;
import br.com.orleon.mq.probe.domain.model.message.ProduceMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.ProductionSettings;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerConfiguration;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerCredentials;
import br.com.orleon.mq.probe.domain.model.message.QueueTarget;
import br.com.orleon.mq.probe.domain.ports.MessageProducerPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProduceMessageUseCaseTest {

    private final MessageProducerPort producerPort = mock(MessageProducerPort.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ProduceMessageUseCase useCase;

    @BeforeEach
    void setup() {
        objectMapper.findAndRegisterModules();
        meterRegistry.clear();
        useCase = new ProduceMessageUseCase(producerPort, idempotencyService, objectMapper, meterRegistry);
    }

    @Test
    void shouldProduceMessagesAndPersistIdempotency() {
        ProduceMessageCommand command = command();
        MessageOperationResult result = result(command);
        when(idempotencyService.find(MessageOperationType.PRODUCE, command.idempotencyKey())).thenReturn(Optional.empty());
        when(producerPort.produce(command)).thenReturn(result);

        MessageOperationResult response = useCase.execute(command);

        assertThat(response).isEqualTo(result);
        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).acquireLock(eq(MessageOperationType.PRODUCE), eq(command.idempotencyKey()), requestCaptor.capture(), eq(null));
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).markCompleted(eq(MessageOperationType.PRODUCE), eq(command.idempotencyKey()), responseCaptor.capture());
        assertThat(requestCaptor.getValue()).contains(command.target().queueName());
        assertThat(responseCaptor.getValue()).contains("processedMessages");
        assertThat(meterRegistry.find("produce.message.time").tag("status", "success").timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }

    @Test
    void shouldReturnCachedResultWhenIdempotentRecordExists() throws Exception {
        ProduceMessageCommand command = command();
        MessageOperationResult expected = result(command);
        String payload = objectMapper.writeValueAsString(expected);
        IdempotencyRecord record = new IdempotencyRecord(
                MessageOperationType.PRODUCE,
                command.idempotencyKey(),
                "hash",
                IdempotencyStatus.COMPLETED,
                Instant.now(),
                Instant.now().plusSeconds(60),
                Instant.now(),
                payload
        );
        when(idempotencyService.find(MessageOperationType.PRODUCE, command.idempotencyKey())).thenReturn(Optional.of(record));

        MessageOperationResult response = useCase.execute(command);

        assertThat(response).usingRecursiveComparison().isEqualTo(expected);
        verify(producerPort, never()).produce(command);
        verify(idempotencyService, never()).acquireLock(eq(MessageOperationType.PRODUCE), eq(command.idempotencyKey()), eq(payload), eq(null));
    }

    @Test
    void shouldMarkFailureWhenProducerThrowsException() {
        ProduceMessageCommand command = command();
        when(idempotencyService.find(MessageOperationType.PRODUCE, command.idempotencyKey())).thenReturn(Optional.empty());
        doThrow(new MessageOperationException("fail")).when(producerPort).produce(command);

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(MessageOperationException.class);
        verify(idempotencyService).markFailed(MessageOperationType.PRODUCE, command.idempotencyKey(), IdempotencyStatus.FAILED);
        assertThat(meterRegistry.find("produce.message.time").tag("status", "failure").timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }

    private ProduceMessageCommand command() {
        QueueManagerConfiguration manager = new QueueManagerConfiguration(
                "QM1",
                "DEV.APP.SVRCONN",
                List.of(new QueueEndpoint("localhost", 1414)),
                new QueueManagerCredentials("user", "pass"),
                false,
                null
        );
        QueueTarget target = new QueueTarget("DEV.QUEUE.1", null);
        ProductionSettings settings = new ProductionSettings(1, 1, 1, Duration.ZERO, Duration.ZERO, true);
        List<MessagePayload> payloads = List.of(new MessagePayload("body", MessagePayload.MessagePayloadFormat.TEXT, Map.of(), Map.of()));
        return new ProduceMessageCommand("key-1", manager, target, payloads, settings);
    }

    private MessageOperationResult result(ProduceMessageCommand command) {
        Instant start = Instant.now();
        return new MessageOperationResult(
                command.idempotencyKey(),
                MessageOperationType.PRODUCE,
                1,
                1,
                start,
                start.plusMillis(10),
                Duration.ofMillis(10),
                Map.of("queue", command.target().queueName()),
                List.of()
        );
    }
}
