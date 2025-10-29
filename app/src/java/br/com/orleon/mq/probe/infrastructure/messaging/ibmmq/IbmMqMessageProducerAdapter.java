package br.com.orleon.mq.probe.infrastructure.messaging.ibmmq;

import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.MessagePayload;
import br.com.orleon.mq.probe.domain.model.message.ProduceMessageCommand;
import br.com.orleon.mq.probe.domain.ports.MessageProducerPort;
import br.com.orleon.mq.probe.infrastructure.config.MqProperties;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import com.ibm.msg.client.wmq.common.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class IbmMqMessageProducerAdapter implements MessageProducerPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(IbmMqMessageProducerAdapter.class);
    private static final String TCP_KEEP_ALIVE_PROPERTY = "XMSC_WMQ_TCP_KEEP_ALIVE";

    private final MqProperties properties;

    public IbmMqMessageProducerAdapter(MqProperties properties) {
        this.properties = properties;
    }

    @Override
    public MessageOperationResult produce(ProduceMessageCommand command) {
        properties.validate();

        Instant start = Instant.now();
        AtomicInteger processed = new AtomicInteger();

        MQQueueConnectionFactory factory = createConnectionFactory();
        LOGGER.debug("Connecting to MQ: host={}, port={}, qm={}, channel={}, tls={}, cipherSuite={}",
                properties.getHost(), properties.getPort(), properties.getQueueManager(), properties.getChannel(),
                properties.isUseTLS(), properties.getCipherSuite());
        try (JMSContext context = createContext(factory)) {
            Destination destination = context.createQueue("queue:///" + command.target().queueName());
            JMSProducer producer = context.createProducer();
            configureProducer(producer, command.settings());
            sendMessages(command, context, destination, producer, processed);
            Instant end = Instant.now();
            return buildResult(command, processed.get(), start, end);
        } catch (JMSException | JMSRuntimeException ex) {
            handleConnectionFailure(ex);
            throw new MessageOperationException("Error connecting to MQ: " + ex.getMessage(), ex);
        }
    }

    protected MQQueueConnectionFactory createConnectionFactory() {
        try {
            MQQueueConnectionFactory factory = new MQQueueConnectionFactory();
            factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
            factory.setHostName(properties.getHost());
            factory.setPort(properties.getPort());
            factory.setQueueManager(properties.getQueueManager());
            factory.setChannel(properties.getChannel());
            factory.setClientReconnectOptions(CommonConstants.WMQ_CLIENT_RECONNECT);
            factory.setClientReconnectTimeout(30);
            factory.setBooleanProperty(TCP_KEEP_ALIVE_PROPERTY, true);
            if (properties.isUseTLS()) {
                factory.setSSLCipherSuite(properties.getCipherSuite());
                factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
            }
            if (StringUtils.hasText(properties.getUsername())) {
                factory.setStringProperty(WMQConstants.USERID, properties.getUsername());
                factory.setStringProperty(WMQConstants.PASSWORD,
                        properties.getPassword() == null ? "" : properties.getPassword());
            }
            return factory;
        } catch (JMSException e) {
            throw new MessageOperationException("Error configuring MQ connection factory: " + e.getMessage(), e);
        }
    }

    protected JMSContext createContext(MQQueueConnectionFactory factory) {
        if (StringUtils.hasText(properties.getUsername())) {
            String password = properties.getPassword() == null ? "" : properties.getPassword();
            return factory.createContext(properties.getUsername(), password);
        }
        return factory.createContext();
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
            command.target().replyToQueueOptional().ifPresent(reply -> {
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

    private void handleConnectionFailure(Exception e) {
        Throwable cause = e.getCause();
        if (cause instanceof SocketException) {
            LOGGER.error("MQ connection reset (possible MQRC 2009). Verify MQ channel, HBINT, TLS, and credentials.", e);
        } else {
            LOGGER.error("Error connecting to MQ: {}", e.getMessage(), e);
        }
    }
}
