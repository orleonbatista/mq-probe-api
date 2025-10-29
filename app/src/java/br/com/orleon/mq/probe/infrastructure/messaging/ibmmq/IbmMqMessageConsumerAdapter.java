package br.com.orleon.mq.probe.infrastructure.messaging.ibmmq;

import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.message.ConsumeMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.model.message.ReceivedMessage;
import br.com.orleon.mq.probe.domain.ports.MessageConsumerPort;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IbmMqMessageConsumerAdapter implements MessageConsumerPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(IbmMqMessageConsumerAdapter.class);

    @Override
    public MessageOperationResult consume(ConsumeMessageCommand command) {
        Instant start = Instant.now();
        Exception lastException = null;

        for (QueueEndpoint endpoint : command.queueManager().endpoints()) {
            try {
                MQQueueConnectionFactory factory = connectionFactory(command, endpoint);
                try (JMSContext context = createContext(factory, command)) {
                    Destination destination = context.createQueue("queue:///" + command.target().queueName());
                    try (JMSConsumer consumer = context.createConsumer(destination)) {
                        List<ReceivedMessage> messages = receiveMessages(command, consumer);
                        Instant end = Instant.now();
                        return buildResult(command, messages, start, end);
                    }
                }
            } catch (Exception ex) {
                lastException = ex;
                LOGGER.error("Failed to consume messages using endpoint {}:{} for queue manager {}", endpoint.host(), endpoint.port(), command.queueManager().name(), ex);
            }
        }

        throw new MessageOperationException("Unable to consume messages from queue " + command.target().queueName(), lastException);
    }

    private MQQueueConnectionFactory connectionFactory(ConsumeMessageCommand command, QueueEndpoint endpoint) throws JMSException {
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

    private JMSContext createContext(MQQueueConnectionFactory factory, ConsumeMessageCommand command) {
        int sessionMode = command.settings().autoAcknowledge() ? JMSContext.AUTO_ACKNOWLEDGE : JMSContext.CLIENT_ACKNOWLEDGE;
        return command.queueManager().credentials().usernameOptional()
                .map(username -> factory.createContext(username, command.queueManager().credentials().passwordOptional().orElse(""), sessionMode))
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
}
