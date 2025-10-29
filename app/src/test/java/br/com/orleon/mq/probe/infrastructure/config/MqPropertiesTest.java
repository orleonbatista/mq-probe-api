package br.com.orleon.mq.probe.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqPropertiesTest {

    @Test
    void shouldValidateWhenRequiredPropertiesPresent() {
        MqProperties properties = new MqProperties();
        properties.setHost("localhost");
        properties.setPort(1414);
        properties.setQueueManager("QM1");
        properties.setChannel("DEV.APP.SVRCONN");
        properties.setUseTLS(false);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldFailValidationWhenHostMissing() {
        MqProperties properties = new MqProperties();
        properties.setPort(1414);
        properties.setQueueManager("QM1");
        properties.setChannel("DEV.APP.SVRCONN");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MQ host is required");
    }

    @Test
    void shouldRequireCipherSuiteWhenTlsEnabled() {
        MqProperties properties = new MqProperties();
        properties.setHost("localhost");
        properties.setPort(1414);
        properties.setQueueManager("QM1");
        properties.setChannel("DEV.APP.SVRCONN");
        properties.setUseTLS(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cipherSuite");
    }
}
