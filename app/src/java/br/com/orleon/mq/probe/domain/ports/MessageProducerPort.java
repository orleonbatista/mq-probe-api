package br.com.orleon.mq.probe.domain.ports;

import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;
import br.com.orleon.mq.probe.domain.model.message.ProduceMessageCommand;

public interface MessageProducerPort {

    MessageOperationResult produce(ProduceMessageCommand command);
}
