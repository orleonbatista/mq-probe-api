# Changelog

## 2025-10-29 — Fase 0 Concluída
- Estrutura inicial do projeto Maven com Java 17 e Spring Boot 3 seguindo Clean Architecture.
- Endpoints de health (liveness e readiness) com integração ao Actuator.
- Configuração de OpenAPI/Swagger e logging JSON.
- Documentação inicial de execução e desenvolvimento atualizada.

## 2025-10-29 — Fase 1 Concluída
- Endpoints `POST /messages/produce` e `POST /messages/consume` com suporte a idempotência via DynamoDB.
- Adapters IBM MQ AllClient para produção e consumo, com métricas e tracing integrados (Datadog/Micrometer).
- Modelo lógico da tabela `mq-probe-idempotency` e tratamento padronizado de respostas idempotentes.
- Mapeamento DTO ↔ domínio, validações e tratadores de exceção dedicados com respostas observáveis.
