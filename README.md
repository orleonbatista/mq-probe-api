# MQ Probe API

Fundação da aplicação MQ Probe, construída com Java 17 e Spring Boot 3 seguindo princípios de Clean Architecture e SOLID.

## Visão Geral
- Endpoints básicos de saúde disponíveis em `/health/live` e `/health/ready`.
- Actuator habilitado com `/actuator/health` e métricas Prometheus em `/actuator/prometheus`.
- Documentação interativa via Swagger UI em `/swagger-ui.html` e especificação OpenAPI em `/api-docs`.
- Logging estruturado em JSON utilizando Logback + Logstash Encoder.
- Núcleo de mensageria com endpoints idempotentes de produção e consumo em `/messages/produce` e `/messages/consume`.

### Idempotência & DynamoDB
- Cada requisição deve informar `idempotencyKey` no corpo e pode repetir o valor no header `Idempotency-Key` (validado).
- O header opcional `Idempotency-Expiry-Seconds` permite customizar o TTL do registro no DynamoDB.
- Modelo lógico completo em [`docs/dynamodb-idempotency.md`](docs/dynamodb-idempotency.md).

### Payloads de Mensagens (resumo)

`POST /messages/produce`

```jsonc
{
  "idempotencyKey": "2f9b6a84-4f85-4a02-9c9f-2f1d5f7b9ad7",
  "queueManager": {
    "name": "QM1",
    "channel": "DEV.APP.SVRCONN",
    "endpoints": [{ "host": "mq.example.com", "port": 1414 }],
    "credentials": { "username": "app", "password": "secret" },
    "useTls": false
  },
  "target": { "queueName": "DEV.QUEUE.1" },
  "settings": {
    "totalMessages": 100,
    "batchSize": 10,
    "concurrency": 4,
    "deliveryDelay": "PT0S",
    "timeToLive": "PT0S",
    "persistent": true
  },
  "messages": [
    {
      "body": "{\"event\":\"probe\"}",
      "format": "JSON",
      "headers": { "Content-Type": "application/json" },
      "properties": { "priority": 5 }
    }
  ]
}
```

`POST /messages/consume`

```jsonc
{
  "idempotencyKey": "8894c582-3d13-4d71-8ec0-622bde904e7f",
  "queueManager": {
    "name": "QM1",
    "channel": "DEV.APP.SVRCONN",
    "endpoints": [{ "host": "mq.example.com", "port": 1414 }]
  },
  "target": { "queueName": "DEV.QUEUE.1" },
  "settings": {
    "maxMessages": 50,
    "waitTimeout": "PT2S",
    "autoAcknowledge": true
  }
}
```

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
- Logs JSON enviados ao stdout com correlação de idempotência e fila alvo.
- Métricas expostas para Prometheus via Actuator (`/actuator/prometheus`).
- Métricas e tracing prontos para Datadog (`management.metrics.export.datadog` e `management.tracing`).

## Próximos Passos
Consulte o plano detalhado em [`TODO.md`](TODO.md) para acompanhar as fases subsequentes do projeto.
