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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IbmMqMessageProducerAdapterTest {

    @Test
    void shouldFailWhenPortConfigurationIsInvalid() {
        MqProperties properties = new MqProperties();
        properties.setHost("localhost");
        properties.setPort(0);
        properties.setQueueManager("QM.DEFAULT");
        properties.setChannel("DEV.APP.SVRCONN");

        IbmMqMessageProducerAdapter adapter = new IbmMqMessageProducerAdapter(properties);

        QueueManagerConfiguration manager = new QueueManagerConfiguration(
                "QM.TEST",
                "DEV.APP.SVRCONN",
                List.of(new QueueEndpoint("", 0)),
                new QueueManagerCredentials("user", "secret"),
                false,
                null
        );
        QueueTarget target = new QueueTarget("DEV.QUEUE.1", null);
        ProductionSettings settings = new ProductionSettings(1, 1, 1, Duration.ZERO, Duration.ZERO, true);
        List<MessagePayload> payloads = List.of(new MessagePayload("body", MessagePayload.MessagePayloadFormat.TEXT, Map.of(), Map.of()));
        ProduceMessageCommand command = new ProduceMessageCommand("key", manager, target, payloads, settings);

        assertThatThrownBy(() -> adapter.produce(command))
                .isInstanceOf(MessageOperationException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(properties.getPort()).isZero();
    }
}
