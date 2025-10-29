package br.com.orleon.mq.probe.infrastructure.messaging.ibmmq;

import br.com.orleon.mq.probe.domain.exception.MessageOperationException;
import br.com.orleon.mq.probe.domain.model.message.MessagePayload;
import br.com.orleon.mq.probe.domain.model.message.ProduceMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.ProductionSettings;
import br.com.orleon.mq.probe.domain.model.message.QueueEndpoint;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerConfiguration;
import br.com.orleon.mq.probe.domain.model.message.QueueManagerCredentials;
import br.com.orleon.mq.probe.domain.model.message.QueueTarget;
import br.com.orleon.mq.probe.infrastructure.config.MqProperties;
import com.ibm.mq.jms.MQQueueConnectionFactory;

import org.junit.jupiter.api.Test;

import javax.jms.JMSContext;
import javax.jms.JMSRuntimeException;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IbmMqMessageProducerAdapterTest {

    @Test
    void shouldWrapSocketExceptionWithMessageOperationException() {
        MqProperties properties = new MqProperties();
        properties.setHost("localhost");
        properties.setPort(1414);
        properties.setQueueManager("QM1");
        properties.setChannel("DEV.APP.SVRCONN");
        properties.setUseTLS(false);

        IbmMqMessageProducerAdapter adapter = new IbmMqMessageProducerAdapter(properties) {
            @Override
            protected JMSContext createContext(MQQueueConnectionFactory factory) {
                throw new JMSRuntimeException("Connection reset", "2009", new SocketException("Connection reset"));
            }
        };

        ProduceMessageCommand command = sampleCommand();

        assertThatThrownBy(() -> adapter.produce(command))
                .isInstanceOf(MessageOperationException.class)
                .hasMessageContaining("Error connecting to MQ");
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
        MessagePayload payload = new MessagePayload("payload", MessagePayload.MessagePayloadFormat.TEXT, Map.of(), Map.of());
        ProductionSettings settings = new ProductionSettings(1, 1, 1, Duration.ZERO, Duration.ZERO, true);
        return new ProduceMessageCommand("id", queueManager, target, List.of(payload), settings);
    }
}
