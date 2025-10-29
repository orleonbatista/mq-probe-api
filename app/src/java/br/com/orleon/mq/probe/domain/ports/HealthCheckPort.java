package br.com.orleon.mq.probe.domain.ports;

import br.com.orleon.mq.probe.domain.model.HealthCheckResult;
import br.com.orleon.mq.probe.domain.model.HealthCheckType;

import java.util.List;

public interface HealthCheckPort {

    List<HealthCheckResult> check(HealthCheckType type);
}
