package br.com.orleon.mq.probe.api.controller;

import br.com.orleon.mq.probe.api.dto.HealthResponse;
import br.com.orleon.mq.probe.application.usecase.GetServiceHealthUseCase;
import br.com.orleon.mq.probe.domain.model.HealthCheckType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Endpoints de verificação de saúde da aplicação")
public class HealthController {

    private final GetServiceHealthUseCase getServiceHealthUseCase;

    public HealthController(GetServiceHealthUseCase getServiceHealthUseCase) {
        this.getServiceHealthUseCase = getServiceHealthUseCase;
    }

    @GetMapping("/live")
    @Operation(summary = "Verifica a liveness da aplicação")
    public HealthResponse liveness() {
        return HealthResponse.fromDomain(getServiceHealthUseCase.execute(HealthCheckType.LIVENESS));
    }

    @GetMapping("/ready")
    @Operation(summary = "Verifica a readiness da aplicação")
    public HealthResponse readiness() {
        return HealthResponse.fromDomain(getServiceHealthUseCase.execute(HealthCheckType.READINESS));
    }
}
