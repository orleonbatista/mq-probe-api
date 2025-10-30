package br.com.orleon.mq.probe.infrastructure.config;

import br.com.orleon.mq.probe.application.usecase.ConsumeMessageUseCase;
import br.com.orleon.mq.probe.application.usecase.ProduceMessageUseCase;
import br.com.orleon.mq.probe.application.service.IdempotencyService;
import br.com.orleon.mq.probe.domain.ports.MessageConsumerPort;
import br.com.orleon.mq.probe.domain.ports.MessageProducerPort;
import br.com.orleon.mq.probe.infrastructure.messaging.ibmmq.IbmMqMessageConsumerAdapter;
import br.com.orleon.mq.probe.infrastructure.messaging.ibmmq.IbmMqMessageProducerAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MqProperties.class)
public class MessagingConfiguration {

    @Bean
    public MessageProducerPort messageProducerPort(MqProperties mqProperties) {
        return new IbmMqMessageProducerAdapter(mqProperties);
    }

    @Bean
    public MessageConsumerPort messageConsumerPort(MqProperties mqProperties) {
        return new IbmMqMessageConsumerAdapter(mqProperties);
    }

    @Bean
    public ProduceMessageUseCase produceMessageUseCase(MessageProducerPort producerPort,
                                                       IdempotencyService idempotencyService,
                                                       ObjectMapper objectMapper,
                                                       MeterRegistry meterRegistry) {
        return new ProduceMessageUseCase(producerPort, idempotencyService, objectMapper, meterRegistry);
    }

    @Bean
    public ConsumeMessageUseCase consumeMessageUseCase(MessageConsumerPort consumerPort,
                                                       IdempotencyService idempotencyService,
                                                       ObjectMapper objectMapper,
                                                       MeterRegistry meterRegistry,
                                                       ObservationRegistry observationRegistry) {
        return new ConsumeMessageUseCase(consumerPort, idempotencyService, objectMapper, meterRegistry, observationRegistry);
    }
}
