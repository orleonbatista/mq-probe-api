package br.com.orleon.mq.probe.domain.ports;

import br.com.orleon.mq.probe.domain.model.message.ConsumeMessageCommand;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationResult;

public interface MessageConsumerPort {

    MessageOperationResult consume(ConsumeMessageCommand command);
}
