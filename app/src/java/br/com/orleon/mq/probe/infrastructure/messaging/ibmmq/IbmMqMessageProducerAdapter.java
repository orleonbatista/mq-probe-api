package br.com.orleon.mq.probe.infrastructure.messaging.ibmmq;

import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.MessagePayload;
import br.com.orleon.mq.probe.domain.model.message.ProduceMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.ports.MessageProducerPort;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import jakarta.jms.BytesMessage;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class IbmMqMessageProducerAdapter implements MessageProducerPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(IbmMqMessageProducerAdapter.class);

    @Override
    public MessageOperationResult produce(ProduceMessageCommand command) {
        Instant start = Instant.now();
        AtomicInteger processed = new AtomicInteger();
        Exception lastException = null;

        for (QueueEndpoint endpoint : command.queueManager().endpoints()) {
            try {
                MQQueueConnectionFactory factory = connectionFactory(command, endpoint);
                try (JMSContext context = createContext(factory, command)) {
                    Destination destination = context.createQueue("queue:///" + command.target().queueName());
                    JMSProducer producer = context.createProducer();
                    configureProducer(producer, command.settings());
                    sendMessages(command, context, destination, producer, processed);
                    Instant end = Instant.now();
                    return buildResult(command, processed.get(), start, end);
                }
            } catch (Exception ex) {
                lastException = ex;
                LOGGER.error("Failed to produce messages using endpoint {}:{} for queue manager {}", endpoint.host(), endpoint.port(), command.queueManager().name(), ex);
            }
        }

        throw new MessageOperationException("Unable to produce messages to queue " + command.target().queueName(), lastException);
    }

    private MQQueueConnectionFactory connectionFactory(ProduceMessageCommand command, QueueEndpoint endpoint) throws JMSException {
        MQQueueConnectionFactory factory = new MQQueueConnectionFactory();
        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
        factory.setHostName(endpoint.host());
        factory.setPort(endpoint.port());
        factory.setQueueManager(command.queueManager().name());
        factory.setChannel(command.queueManager().channel());
        if (command.queueManager().useTls()) {
            factory.setSSLCipherSuite(command.queueManager().cipherSuite());
            factory.setBooleanProperty(WMQConstants.WMQ_SSL_FIPS_REQUIRED, false);
            factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
        }
        return factory;
    }

    private JMSContext createContext(MQQueueConnectionFactory factory, ProduceMessageCommand command) {
        return command.queueManager().credentials().username()
                .map(username -> factory.createContext(username, command.queueManager().credentials().password().orElse("")))
                .orElseGet(factory::createContext);
    }

    private void configureProducer(JMSProducer producer, br.com.orleon.mq.probe.domain.model.message.ProductionSettings settings) {
        producer.setDeliveryMode(settings.persistent() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
        if (!settings.timeToLive().isZero()) {
            producer.setTimeToLive(settings.timeToLive().toMillis());
        }
        if (!settings.deliveryDelay().isZero()) {
            producer.setDeliveryDelay(settings.deliveryDelay().toMillis());
        }
    }

    private void sendMessages(ProduceMessageCommand command,
                              JMSContext context,
                              Destination destination,
                              JMSProducer producer,
                              AtomicInteger processed) throws JMSException {
        List<MessagePayload> payloads = command.payloads();
        if (payloads == null || payloads.isEmpty()) {
            throw new MessageOperationException("At least one message payload must be provided");
        }

        int total = command.settings().totalMessages();
        for (int i = 0; i < total; i++) {
            MessagePayload payload = payloads.get(i % payloads.size());
            Message message = createMessage(context, payload);
            command.target().replyToQueue().ifPresent(reply -> {
                try {
                    message.setJMSReplyTo(context.createQueue("queue:///" + reply));
                } catch (JMSException e) {
                    throw new MessageOperationException("Failed to set replyTo queue", e);
                }
            });
            setProperties(message, payload.properties());
            setHeaders(message, payload.headers());
            producer.send(destination, message);
            processed.incrementAndGet();
        }
    }

    private Message createMessage(JMSContext context, MessagePayload payload) throws JMSException {
        return switch (payload.format()) {
            case TEXT, JSON -> createTextMessage(context, payload.body());
            case BINARY -> createBytesMessage(context, payload.body());
        };
    }

    private TextMessage createTextMessage(JMSContext context, String body) {
        return context.createTextMessage(body);
    }

    private BytesMessage createBytesMessage(JMSContext context, String body) throws JMSException {
        BytesMessage message = context.createBytesMessage();
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        message.writeBytes(bytes);
        return message;
    }

    private void setProperties(Message message, Map<String, Object> properties) {
        properties.forEach((key, value) -> {
            try {
                message.setObjectProperty(key, value);
            } catch (JMSException e) {
                throw new MessageOperationException("Failed to set JMS property " + key, e);
            }
        });
    }

    private void setHeaders(Message message, Map<String, String> headers) {
        headers.forEach((key, value) -> {
            try {
                message.setStringProperty(key, value);
            } catch (JMSException e) {
                throw new MessageOperationException("Failed to set JMS header " + key, e);
            }
        });
    }

    private MessageOperationResult buildResult(ProduceMessageCommand command,
                                               int processedMessages,
                                               Instant startedAt,
                                               Instant completedAt) {
        Duration elapsed = Duration.between(startedAt, completedAt);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("queueManager", command.queueManager().name());
        metadata.put("queue", command.target().queueName());
        metadata.put("batchSize", command.settings().batchSize());
        metadata.put("concurrency", command.settings().concurrency());
        return new MessageOperationResult(
                command.idempotencyKey(),
                MessageOperationType.PRODUCE,
                command.settings().totalMessages(),
                processedMessages,
                startedAt,
                completedAt,
                elapsed,
                metadata,
                List.of()
        );
    }
}
