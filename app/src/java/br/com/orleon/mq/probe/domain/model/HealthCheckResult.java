package br.com.orleon.mq.probe.domain.model;

import java.time.Instant;

public record HealthCheckResult(String probe, String status, String details, Instant checkedAt) {
}
