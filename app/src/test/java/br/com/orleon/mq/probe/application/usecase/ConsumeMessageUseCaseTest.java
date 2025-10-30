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
import br.com.orleon.mq.probe.domain.model.message.ConsumeMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.ConsumptionSettings;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerConfiguration;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerCredentials;
import br.com.orleon.mq.probe.domain.model.message.QueueTarget;
import br.com.orleon.mq.probe.domain.model.message.ReceivedMessage;
import br.com.orleon.mq.probe.domain.ports.MessageConsumerPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsumeMessageUseCaseTest {

    private final MessageConsumerPort consumerPort = mock(MessageConsumerPort.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ObservationRegistry observationRegistry = ObservationRegistry.create();
    private ConsumeMessageUseCase useCase;

    @BeforeEach
    void setup() {
        objectMapper.findAndRegisterModules();
        meterRegistry.clear();
        useCase = new ConsumeMessageUseCase(consumerPort, idempotencyService, objectMapper, meterRegistry, observationRegistry);
    }

    @Test
    void shouldConsumeMessagesAndPersistIdempotency() {
        ConsumeMessageCommand command = command();
        MessageOperationResult result = result(command);
        when(idempotencyService.find(MessageOperationType.CONSUME, command.idempotencyKey())).thenReturn(Optional.empty());
        when(consumerPort.consume(command)).thenReturn(result);

        MessageOperationResult response = useCase.execute(command);

        assertThat(response).isEqualTo(result);
        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).acquireLock(eq(MessageOperationType.CONSUME), eq(command.idempotencyKey()), requestCaptor.capture(), eq(null));
        assertThat(requestCaptor.getValue()).contains(command.target().queueName());
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).markCompleted(eq(MessageOperationType.CONSUME), eq(command.idempotencyKey()), responseCaptor.capture());
        assertThat(responseCaptor.getValue()).contains("processedMessages");
    }

    @Test
    void shouldReturnCachedResultWhenAvailable() throws Exception {
        ConsumeMessageCommand command = command();
        MessageOperationResult expected = result(command);
        String payload = objectMapper.writeValueAsString(expected);
        IdempotencyRecord record = new IdempotencyRecord(
                MessageOperationType.CONSUME,
                command.idempotencyKey(),
                "hash",
                IdempotencyStatus.COMPLETED,
                Instant.now(),
                Instant.now().plusSeconds(60),
                Instant.now(),
                payload
        );
        when(idempotencyService.find(MessageOperationType.CONSUME, command.idempotencyKey())).thenReturn(Optional.of(record));

        MessageOperationResult response = useCase.execute(command);

        assertThat(response).usingRecursiveComparison().isEqualTo(expected);
        verify(consumerPort, never()).consume(command);
        verify(idempotencyService, never()).acquireLock(eq(MessageOperationType.CONSUME), eq(command.idempotencyKey()), eq(payload), eq(null));
    }

    @Test
    void shouldMarkFailureWhenConsumerThrowsException() {
        ConsumeMessageCommand command = command();
        when(idempotencyService.find(MessageOperationType.CONSUME, command.idempotencyKey())).thenReturn(Optional.empty());
        doThrow(new MessageOperationException("fail")).when(consumerPort).consume(command);

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(MessageOperationException.class);
        verify(idempotencyService).markFailed(MessageOperationType.CONSUME, command.idempotencyKey(), IdempotencyStatus.FAILED);
    }

    private ConsumeMessageCommand command() {
        QueueManagerConfiguration manager = new QueueManagerConfiguration(
                "QM1",
                "DEV.APP.SVRCONN",
                List.of(new QueueEndpoint("localhost", 1414)),
                new QueueManagerCredentials("user", "pass"),
                false,
                null
        );
        QueueTarget target = new QueueTarget("DEV.QUEUE.1", null);
        ConsumptionSettings settings = new ConsumptionSettings(1, Duration.ofSeconds(1), true);
        return new ConsumeMessageCommand("key-2", manager, target, settings);
    }

    private MessageOperationResult result(ConsumeMessageCommand command) {
        Instant start = Instant.now();
        return new MessageOperationResult(
                command.idempotencyKey(),
                MessageOperationType.CONSUME,
                1,
                1,
                start,
                start.plusMillis(15),
                Duration.ofMillis(15),
                Map.of("queue", command.target().queueName()),
                List.of(new ReceivedMessage("id", "body", Map.of(), Map.of()))
        );
    }
}
