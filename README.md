# MQ Probe API

Fundação da aplicação MQ Probe, construída com Java 17 e Spring Boot 3 seguindo princípios de Clean Architecture e SOLID.

## Visão Geral
- Endpoints básicos de saúde disponíveis em `/health/live` e `/health/ready`.
- Actuator habilitado com `/actuator/health` e métricas Prometheus em `/actuator/prometheus`.
- Documentação interativa via Swagger UI em `/swagger-ui.html` e especificação OpenAPI em `/api-docs`.
- Logging estruturado em JSON utilizando Logback + Logstash Encoder.

## Requisitos
- Java 17 (JDK).
- Maven 3.9+.

## Estrutura de Pastas
```
app/
  pom.xml
  settings.xml
  src/
    java/br/com/orleon/mq/probe/...
    resources/
      application.yaml
      logback-spring.xml
    test/java/br/com/orleon/mq/probe/...
```

## Executando a aplicação
1. Instale as dependências e execute a aplicação:
   ```bash
   mvn -f app/pom.xml -s app/settings.xml spring-boot:run
   ```
2. Acesse:
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health Liveness: http://localhost:8080/health/live
   - Health Readiness: http://localhost:8080/health/ready
   - Actuator Health: http://localhost:8080/actuator/health

## Testes
Execute a suíte de testes com:
```bash
mvn -f app/pom.xml -s app/settings.xml test
```

## Observabilidade
- Logs JSON enviados ao stdout.
- Métricas expostas para Prometheus via Actuator.

## Próximos Passos
Consulte o plano detalhado em [`TODO.md`](TODO.md) para acompanhar as fases subsequentes do projeto.
