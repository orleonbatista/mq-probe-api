package br.com.orleon.mq.probe.api.mapper;

import br.com.orleon.mq.probe.api.dto.messages.ConsumeMessageRequest;
import br.com.orleon.mq.probe.api.dto.messages.EndpointRequest;
import br.com.orleon.mq.probe.api.dto.messages.MessageOperationResponse;
import br.com.orleon.mq.probe.api.dto.messages.MessagePayloadRequest;
import br.com.orleon.mq.probe.api.dto.messages.ProduceMessageRequest;
import br.com.orleon.mq.probe.api.dto.messages.QueueManagerRequest;
import br.com.orleon.mq.probe.api.dto.messages.ReceivedMessageResponse;
import br.com.orleon.mq.probe.domain.model.message.ConsumeMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.ConsumptionSettings;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessagePayload;
import br.com.orleon.mq.probe.domain.model.message.ProduceMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.ProductionSettings;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerConfiguration;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerCredentials;
import br.com.orleon.mq.probe.domain.model.message.QueueTarget;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public ProduceMessageCommand toCommand(ProduceMessageRequest request) {
        QueueManagerConfiguration queueManager = toQueueManager(request.queueManager());
        QueueTarget target = new QueueTarget(request.target().queueName(), request.target().replyToQueue());
        ProductionSettings settings = new ProductionSettings(
                request.settings().totalMessages(),
                request.settings().batchSize(),
                request.settings().concurrency(),
                Duration.parse(request.settings().deliveryDelay()),
                Duration.parse(request.settings().timeToLive()),
                request.settings().persistent()
        );
        List<MessagePayload> payloads = request.messages().stream()
                .map(this::toPayload)
                .toList();
        return new ProduceMessageCommand(request.idempotencyKey(), queueManager, target, payloads, settings);
    }

    public ConsumeMessageCommand toCommand(ConsumeMessageRequest request) {
        QueueManagerConfiguration queueManager = toQueueManager(request.queueManager());
        QueueTarget target = new QueueTarget(request.target().queueName(), request.target().replyToQueue());
        ConsumptionSettings settings = new ConsumptionSettings(
                request.settings().maxMessages(),
                Duration.parse(request.settings().waitTimeout()),
                request.settings().autoAcknowledge()
        );
        return new ConsumeMessageCommand(request.idempotencyKey(), queueManager, target, settings);
    }

    public MessageOperationResponse toResponse(MessageOperationResult result) {
        List<ReceivedMessageResponse> messages = result.receivedMessages() == null ? List.of() : result.receivedMessages().stream()
                .map(message -> new ReceivedMessageResponse(message.messageId(), message.body(), message.properties(), message.headers()))
                .toList();
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        return new MessageOperationResponse(
                result.idempotencyKey(),
                result.operationType().name(),
                result.requestedMessages(),
                result.processedMessages(),
                result.startedAt(),
                result.completedAt(),
                result.elapsed(),
                metadata,
                messages
        );
    }

    private QueueManagerConfiguration toQueueManager(QueueManagerRequest request) {
        List<QueueEndpoint> endpoints = request.endpoints().stream()
                .map(this::toEndpoint)
                .toList();
        QueueManagerCredentials credentials = request.credentials() == null ? new QueueManagerCredentials(null, null)
                : new QueueManagerCredentials(request.credentials().username(), request.credentials().password());
        return new QueueManagerConfiguration(request.name(), request.channel(), endpoints, credentials, request.useTls(), request.cipherSuite());
    }

    private QueueEndpoint toEndpoint(EndpointRequest endpoint) {
        return new QueueEndpoint(endpoint.host(), endpoint.port());
    }

    private MessagePayload toPayload(MessagePayloadRequest request) {
        MessagePayload.MessagePayloadFormat format = MessagePayload.MessagePayloadFormat.valueOf(request.format().toUpperCase(Locale.ROOT));
        return new MessagePayload(request.body(), format, request.headers(), request.properties());
    }
}
