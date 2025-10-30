package br.com.orleon.mq.probe.infrastructure.messaging.ibmmq;

import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.message.ConsumeMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerCredentials;
import br.com.orleon.mq.probe.domain.model.message.ReceivedMessage;
import br.com.orleon.mq.probe.domain.ports.MessageConsumerPort;
import br.com.orleon.mq.probe.infrastructure.config.MqProperties;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.jms.JmsConstants;
import com.ibm.msg.client.wmq.common.CommonConstants;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.SocketException;

public class IbmMqMessageConsumerAdapter implements MessageConsumerPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(IbmMqMessageConsumerAdapter.class);
    private static final String QUEUE_PREFIX = CommonConstants.QUEUE_PREFIX;
    private static final String TCP_KEEP_ALIVE_PROPERTY = "WMQ_TCP_KEEP_ALIVE";

    private final MqProperties properties;

    public IbmMqMessageConsumerAdapter(MqProperties properties) {
        this.properties = properties;
    }

    @Override
    public MessageOperationResult consume(ConsumeMessageCommand command) {
        Instant start = Instant.now();
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
                try (JMSContext context = createContext(factory, configuration.credentials(), command.settings().autoAcknowledge())) {
                    Destination destination = context.createQueue(QUEUE_PREFIX + command.target().queueName());
                    try (JMSConsumer consumer = context.createConsumer(destination)) {
                        List<ReceivedMessage> messages = receiveMessages(command, consumer);
                        Instant end = Instant.now();
                        return buildResult(command, messages, start, end);
                    }
                }
            } catch (JMSException ex) {
                lastException = new MessageOperationException("Error connecting to MQ: " + ex.getMessage(), ex);
                if (ex.getCause() instanceof SocketException) {
                    LOGGER.error("MQ connection reset (possible MQRC 2009). Verify MQ channel, HBINT, TLS, and credentials.");
                }
                LOGGER.error("Failed to consume messages using endpoint {}:{} for queue manager {}", endpoint.host(), endpoint.port(), command.queueManager().name(), ex);
            } catch (RuntimeException ex) {
                lastException = ex;
                LOGGER.error("Failed to consume messages using endpoint {}:{} for queue manager {}", endpoint.host(), endpoint.port(), command.queueManager().name(), ex);
            }
        }

        throw new MessageOperationException("Unable to consume messages from queue " + command.target().queueName(), lastException);
    }

    private ConnectionConfiguration buildConfiguration(ConsumeMessageCommand command, QueueEndpoint endpoint) {
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

    private JMSContext createContext(MQQueueConnectionFactory factory,
                                     QueueManagerCredentials credentials,
                                     boolean autoAcknowledge) {
        int sessionMode = autoAcknowledge ? JMSContext.AUTO_ACKNOWLEDGE : JMSContext.CLIENT_ACKNOWLEDGE;
        return credentials.usernameOptional()
                .filter(StringUtils::hasText)
                .map(username -> factory.createContext(username, credentials.passwordOptional().orElse(""), sessionMode))
                .orElseGet(() -> factory.createContext(sessionMode));
    }

    private List<ReceivedMessage> receiveMessages(ConsumeMessageCommand command, JMSConsumer consumer) throws JMSException {
        List<ReceivedMessage> messages = new ArrayList<>();
        Duration timeout = command.settings().waitTimeout();
        long timeoutMillis = timeout.toMillis();
        for (int i = 0; i < command.settings().maxMessages(); i++) {
            Message message = timeoutMillis <= 0 ? consumer.receiveNoWait() : consumer.receive(timeoutMillis);
            if (message == null) {
                break;
            }
            ReceivedMessage received = mapMessage(message);
            messages.add(received);
            if (!command.settings().autoAcknowledge()) {
                message.acknowledge();
            }
        }
        return messages;
    }

    private ReceivedMessage mapMessage(Message message) throws JMSException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        var propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements()) {
            String name = propertyNames.nextElement().toString();
            properties.put(name, message.getObjectProperty(name));
        }
        String body;
        if (message instanceof TextMessage textMessage) {
            body = textMessage.getText();
        } else {
            body = message.getBody(String.class);
        }
        headers.put("JMSCorrelationID", message.getJMSCorrelationID());
        headers.put("JMSMessageID", message.getJMSMessageID());
        return new ReceivedMessage(message.getJMSMessageID(), body, properties, headers);
    }

    private MessageOperationResult buildResult(ConsumeMessageCommand command,
                                               List<ReceivedMessage> messages,
                                               Instant startedAt,
                                               Instant completedAt) {
        Duration elapsed = Duration.between(startedAt, completedAt);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("queueManager", command.queueManager().name());
        metadata.put("queue", command.target().queueName());
        metadata.put("requestedMessages", command.settings().maxMessages());
        return new MessageOperationResult(
                command.idempotencyKey(),
                MessageOperationType.CONSUME,
                command.settings().maxMessages(),
                messages.size(),
                startedAt,
                completedAt,
                elapsed,
                metadata,
                List.copyOf(messages)
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
