package br.com.orleon.mq.probe.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mqprobe.idempotency")
public record IdempotencyProperties(
        @NotBlank String tableName,
        String region,
        String endpointOverride,
        @DefaultValue("PT24H") Duration recordTtl
) {
}
