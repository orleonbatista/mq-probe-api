package br.com.orleon.mq.probe.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI mqProbeOpenApi() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title("MQ Probe API")
                        .description("Ferramenta de observabilidade e testes para integrações MQ")
                        .version("0.0.1")
                        .license(new License().name("Apache 2.0"))
                        .contact(new Contact().name("Orleon MQ Team")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentação do projeto")
                        .url("https://github.com/orleon/mq-probe"));
    }
}
