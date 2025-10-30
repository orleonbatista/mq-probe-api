package br.com.orleon.mq.probe.infrastructure.messaging.ibmmq;

import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.MessagePayload;
import br.com.orleon.mq.probe.domain.model.message.ProduceMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerCredentials;
import br.com.orleon.mq.probe.domain.ports.MessageProducerPort;
import br.com.orleon.mq.probe.infrastructure.config.MqProperties;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.jms.JmsConstants;
import com.ibm.msg.client.wmq.common.CommonConstants;
import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

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
    private static final String QUEUE_PREFIX = CommonConstants.QUEUE_PREFIX;
    private static final String TCP_KEEP_ALIVE_PROPERTY = "WMQ_TCP_KEEP_ALIVE";

    private final MqProperties properties;

    public IbmMqMessageProducerAdapter(MqProperties properties) {
        this.properties = properties;
    }

    @Override
    public MessageOperationResult produce(ProduceMessageCommand command) {
        Instant start = Instant.now();
        AtomicInteger processed = new AtomicInteger();
        Exception lastException = null;

        for (QueueEndpoint endpoint : command.queueManager().endpoints()) {
            try {
                ConnectionConfiguration configuration = buildConfiguration(command, endpoint);
                MQQueueConnectionFactory factory = connectionFactory(configuration);
                LOGGER.debug("Connecting to MQ: host={}, port={}, qm={}, channel={}, tls={}, cipherSuite={}",
                        configuration.host(),
                        configuration.port(),
                        configuration.queueManager(),
                        configuration.channel(),
                        configuration.useTls(),
                        configuration.cipherSuite());
                try (JMSContext context = createContext(factory, configuration.credentials())) {
                    Destination destination = context.createQueue(QUEUE_PREFIX + command.target().queueName());
                    JMSProducer producer = context.createProducer();
                    configureProducer(producer, command.settings());
                    sendMessages(command, context, destination, producer, processed);
                    Instant end = Instant.now();
                    return buildResult(command, processed.get(), start, end);
                }
            } catch (JMSException ex) {
                lastException = new MessageOperationException("Error connecting to MQ: " + ex.getMessage(), ex);
                if (ex.getCause() instanceof SocketException) {
                    LOGGER.error("MQ connection reset (possible MQRC 2009). Verify MQ channel, HBINT, TLS, and credentials.");
                }
                LOGGER.error("Failed to produce messages using endpoint {}:{} for queue manager {}", endpoint.host(), endpoint.port(), command.queueManager().name(), ex);
            } catch (RuntimeException ex) {
                lastException = ex;
                LOGGER.error("Failed to produce messages using endpoint {}:{} for queue manager {}", endpoint.host(), endpoint.port(), command.queueManager().name(), ex);
            }
        }

        throw new MessageOperationException("Unable to produce messages to queue " + command.target().queueName(), lastException);
    }

    private ConnectionConfiguration buildConfiguration(ProduceMessageCommand command, QueueEndpoint endpoint) {
        String host = StringUtils.hasText(endpoint.host()) ? endpoint.host() : properties.getHost();
        int port = endpoint.port() > 0 ? endpoint.port() : properties.getPort();
        String queueManager = StringUtils.hasText(command.queueManager().name()) ? command.queueManager().name() : properties.getQueueManager();
        String channel = StringUtils.hasText(command.queueManager().channel()) ? command.queueManager().channel() : properties.getChannel();
        boolean useTls = command.queueManager().useTls() || properties.isUseTls();
        String cipherSuite = useTls ? (StringUtils.hasText(command.queueManager().cipherSuite()) ? command.queueManager().cipherSuite() : properties.getCipherSuite()) : null;
        QueueManagerCredentials credentials = command.queueManager().credentials();
        String username = credentials.usernameOptional().filter(StringUtils::hasText).orElseGet(properties::getUsername);
        String password = credentials.passwordOptional().orElseGet(properties::getPassword);

        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("MQ host must be provided");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("MQ port must be greater than zero");
        }
        if (!StringUtils.hasText(queueManager)) {
            throw new IllegalArgumentException("MQ queue manager must be provided");
        }
        if (!StringUtils.hasText(channel)) {
            throw new IllegalArgumentException("MQ channel must be provided");
        }
        if (useTls && !StringUtils.hasText(cipherSuite)) {
            throw new IllegalArgumentException("MQ cipher suite must be provided when TLS is enabled");
        }

        return new ConnectionConfiguration(host, port, queueManager, channel, useTls, cipherSuite, username, password);
    }

    private MQQueueConnectionFactory connectionFactory(ConnectionConfiguration configuration) throws JMSException {
        MQQueueConnectionFactory factory = new MQQueueConnectionFactory();
        factory.setTransportType(CommonConstants.WMQ_CM_CLIENT);
        factory.setHostName(configuration.host());
        factory.setPort(configuration.port());
        factory.setQueueManager(configuration.queueManager());
        factory.setChannel(configuration.channel());
        factory.setIntProperty(CommonConstants.WMQ_CLIENT_RECONNECT_OPTIONS, CommonConstants.WMQ_CLIENT_RECONNECT);
        factory.setIntProperty(CommonConstants.WMQ_CLIENT_RECONNECT_TIMEOUT, 30);
        factory.setBooleanProperty(TCP_KEEP_ALIVE_PROPERTY, true);
        if (configuration.useTls()) {
            factory.setSSLCipherSuite(configuration.cipherSuite());
            factory.setBooleanProperty(CommonConstants.WMQ_SSL_FIPS_REQUIRED, false);
            factory.setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true);
        }
        if (StringUtils.hasText(configuration.username())) {
            factory.setStringProperty(JmsConstants.USERID, configuration.username());
            if (configuration.password() != null) {
                factory.setStringProperty(JmsConstants.PASSWORD, configuration.password());
            }
        }
        return factory;
    }

    private JMSContext createContext(MQQueueConnectionFactory factory, QueueManagerCredentials credentials) {
        return credentials.usernameOptional()
                .filter(StringUtils::hasText)
                .map(username -> factory.createContext(username, credentials.passwordOptional().orElse("")))
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
            command.target().replyToQueueOptional().ifPresent(reply -> {
                try {
                    message.setJMSReplyTo(context.createQueue(QUEUE_PREFIX + reply));
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

    private record ConnectionConfiguration(
            String host,
            int port,
            String queueManager,
            String channel,
            boolean useTls,
            String cipherSuite,
            String username,
            String password
    ) {
        QueueManagerCredentials credentials() {
            return new QueueManagerCredentials(username, password);
        }
    }
}
