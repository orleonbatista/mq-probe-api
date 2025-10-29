package br.com.orleon.mq.probe.domain.model;

import java.time.Instant;
import java.util.List;

public record ApplicationHealth(String status, Instant checkedAt, List<HealthCheckResult> probes) {
}
