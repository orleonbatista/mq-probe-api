package br.com.orleon.mq.probe.infrastructure.messaging.ibmmq;

import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.message.ConsumeMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.ReceivedMessage;
import br.com.orleon.mq.probe.domain.ports.MessageConsumerPort;
import br.com.orleon.mq.probe.infrastructure.config.MqProperties;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import com.ibm.msg.client.wmq.common.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.net.SocketException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IbmMqMessageConsumerAdapter implements MessageConsumerPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(IbmMqMessageConsumerAdapter.class);
    private static final String TCP_KEEP_ALIVE_PROPERTY = "XMSC_WMQ_TCP_KEEP_ALIVE";

    private final MqProperties properties;

    public IbmMqMessageConsumerAdapter(MqProperties properties) {
        this.properties = properties;
    }

    @Override
    public MessageOperationResult consume(ConsumeMessageCommand command) {
        properties.validate();

        Instant start = Instant.now();
        MQQueueConnectionFactory factory = createConnectionFactory();
        LOGGER.debug("Connecting to MQ: host={}, port={}, qm={}, channel={}, tls={}, cipherSuite={}",
                properties.getHost(), properties.getPort(), properties.getQueueManager(), properties.getChannel(),
                properties.isUseTLS(), properties.getCipherSuite());
        int sessionMode = command.settings().autoAcknowledge() ? JMSContext.AUTO_ACKNOWLEDGE : JMSContext.CLIENT_ACKNOWLEDGE;

        try (JMSContext context = createContext(factory, sessionMode)) {
            Destination destination = context.createQueue("queue:///" + command.target().queueName());
            try (JMSConsumer consumer = context.createConsumer(destination)) {
                List<ReceivedMessage> messages = receiveMessages(command, consumer);
                Instant end = Instant.now();
                return buildResult(command, messages, start, end);
            }
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

    protected JMSContext createContext(MQQueueConnectionFactory factory, int sessionMode) {
        if (StringUtils.hasText(properties.getUsername())) {
            String password = properties.getPassword() == null ? "" : properties.getPassword();
            return factory.createContext(properties.getUsername(), password, sessionMode);
        }
        return factory.createContext(sessionMode);
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

    private void handleConnectionFailure(Exception e) {
        Throwable cause = e.getCause();
        if (cause instanceof SocketException) {
            LOGGER.error("MQ connection reset (possible MQRC 2009). Verify MQ channel, HBINT, TLS, and credentials.", e);
        } else {
            LOGGER.error("Error connecting to MQ: {}", e.getMessage(), e);
        }
    }
}
