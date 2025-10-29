package br.com.orleon.mq.probe.application.usecase;

import br.com.orleon.mq.probe.domain.model.ApplicationHealth;
import br.com.orleon.mq.probe.domain.model.HealthCheckResult;
import br.com.orleon.mq.probe.domain.model.HealthCheckType;
import br.com.orleon.mq.probe.domain.ports.HealthCheckPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class GetServiceHealthUseCase {

    private final HealthCheckPort healthCheckPort;

    public GetServiceHealthUseCase(HealthCheckPort healthCheckPort) {
        this.healthCheckPort = healthCheckPort;
    }

    public ApplicationHealth execute(HealthCheckType type) {
        List<HealthCheckResult> probes = healthCheckPort.check(type);
        String globalStatus = probes.stream()
                .allMatch(result -> "UP".equalsIgnoreCase(result.status())) ? "UP" : "DOWN";
        return new ApplicationHealth(globalStatus, Instant.now(), probes);
    }
}
