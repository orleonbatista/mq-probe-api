package br.com.orleon.mq.probe.api.dto;

import br.com.orleon.mq.probe.domain.model.ApplicationHealth;
import br.com.orleon.mq.probe.domain.model.HealthCheckResult;

import java.time.Instant;
import java.util.List;

public record HealthResponse(String status, Instant checkedAt, List<HealthCheckResult> probes) {
    public static HealthResponse fromDomain(ApplicationHealth health) {
        return new HealthResponse(health.status(), health.checkedAt(), health.probes());
    }
}
