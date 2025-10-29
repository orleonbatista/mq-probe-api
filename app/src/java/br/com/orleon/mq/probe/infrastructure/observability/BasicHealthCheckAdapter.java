package br.com.orleon.mq.probe.infrastructure.observability;

import br.com.orleon.mq.probe.domain.model.HealthCheckResult;
import br.com.orleon.mq.probe.domain.model.HealthCheckType;
import br.com.orleon.mq.probe.domain.ports.HealthCheckPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BasicHealthCheckAdapter implements HealthCheckPort {

    private final String applicationName;

    public BasicHealthCheckAdapter(@Value("${spring.application.name:mq-probe-api}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public List<HealthCheckResult> check(HealthCheckType type) {
        String probeName = type.name().toLowerCase();
        HealthCheckResult result = new HealthCheckResult(
                probeName,
                "UP",
                String.format("%s %s probe is operational", applicationName, probeName),
                Instant.now()
        );
        return List.of(result);
    }
}
