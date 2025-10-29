package br.com.orleon.mq.probe.application.usecase;

import br.com.orleon.mq.probe.application.service.IdempotencyService;
import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProduceMessageUseCaseTest {

    private MessageProducerPort producerPort;
    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private ObservationRegistry observationRegistry;
    private ProduceMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        producerPort = mock(MessageProducerPort.class);
        idempotencyService = mock(IdempotencyService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = ObservationRegistry.create();
        useCase = new ProduceMessageUseCase(producerPort, idempotencyService, objectMapper, meterRegistry, observationRegistry);

        when(idempotencyService.find(any(), any())).thenReturn(Optional.empty());
        doNothing().when(idempotencyService).acquireLock(any(), any(), any(), any());
    }

    @Test
    void shouldRecordSuccessMetricWhenMessageProduced() {
        ProduceMessageCommand command = sampleCommand();
        MessageOperationResult result = sampleResult(command);
        when(producerPort.produce(command)).thenReturn(result);

        useCase.execute(command);

        Timer successTimer = meterRegistry.find("produce.message.time").tag("status", "success").timer();
        assertThat(successTimer).isNotNull();
        assertThat(successTimer.count()).isEqualTo(1L);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).markCompleted(any(), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isNotBlank();
    }

    @Test
    void shouldRecordFailureMetricAndRethrowException() {
        ProduceMessageCommand command = sampleCommand();
        when(producerPort.produce(command)).thenThrow(new MessageOperationException("failure"));

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(MessageOperationException.class);

        Timer failureTimer = meterRegistry.find("produce.message.time").tag("status", "failure").timer();
        assertThat(failureTimer).isNotNull();
        assertThat(failureTimer.count()).isEqualTo(1L);
        verify(idempotencyService).markFailed(MessageOperationType.PRODUCE, command.idempotencyKey(), IdempotencyStatus.FAILED);
    }

    private ProduceMessageCommand sampleCommand() {
        QueueManagerConfiguration queueManager = new QueueManagerConfiguration(
                "QM1",
                "DEV.APP.SVRCONN",
                List.of(new QueueEndpoint("localhost", 1414)),
                new QueueManagerCredentials("user", "secret"),
                false,
                null
        );
        QueueTarget target = new QueueTarget("DEV.QUEUE.1", null);
        MessagePayload payload = new MessagePayload("body", MessagePayload.MessagePayloadFormat.TEXT, Map.of(), Map.of());
        ProductionSettings settings = new ProductionSettings(1, 1, 1, Duration.ZERO, Duration.ZERO, true);
        return new ProduceMessageCommand("id-123", queueManager, target, List.of(payload), settings);
    }

    private MessageOperationResult sampleResult(ProduceMessageCommand command) {
        Instant now = Instant.now();
        return new MessageOperationResult(
                command.idempotencyKey(),
                MessageOperationType.PRODUCE,
                command.settings().totalMessages(),
                command.settings().totalMessages(),
                now,
                now,
                Duration.ZERO,
                Map.of(),
                List.of()
        );
    }
}
