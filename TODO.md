# 🧭 MQ Probe – Plano de Implementação por Fases

## Regras de Uso do Plano
- Marque o título da fase com ✅ assim que concluída (ex.: `## ✅ Fase 1 — Núcleo`).
- A cada tarefa finalizada, marque a checkbox correspondente (`☐` → `✅`).
- Ao concluir uma fase, adicione uma entrada resumida no `CHANGELOG` descrevendo a entrega.

## ☐ Fase 0 — Fundação / Scaffold + DX
**Objetivo:** Criar o esqueleto da aplicação Java 17 com Spring Boot 3.x seguindo Clean Architecture + SOLID.

- ☐ Configurar Gradle e plugin para Java 17.
- ☐ Definir dependências iniciais (Spring Boot 3.x, validações, observabilidade).
- ☐ Estruturar pacotes seguindo Clean Architecture + SOLID.
- ☐ Implementar health endpoints básicos.
- ☐ Configurar Swagger/OpenAPI funcional desde a fase inicial.
- ☐ Habilitar logging em formato JSON.
- ☐ Atualizar README com instruções básicas de execução e desenvolvimento.

## ☐ Fase 1 — Núcleo (Produce / Consume + Idempotência)
**Objetivo:** Implementar os endpoints `POST /messages/produce` e `POST /messages/consume` com idempotência via DynamoDB.

- ☐ Modelar a tabela `mq-probe-idempotency` no DynamoDB.
- ☐ Implementar fluxos de produção e consumo com idempotência.
- ☐ Criar adapters utilizando IBM MQ AllClient.
- ☐ Definir ports e use cases segundo Clean Architecture.
- ☐ Adicionar observabilidade (logs, métricas, tracing) aos fluxos principais.

## ☐ Fase 2 — Utilidades (Depth, Connectivity, Status, Config)
**Objetivo:** Criar endpoints auxiliares (`/queue/depth`, `/connections/test`, `/status`, `/version`, `/metrics`, `/config`).

- ☐ Implementar endpoint de profundidade de fila (`/queue/depth`).
- ☐ Criar endpoint de teste de conexão (`/connections/test`).
- ☐ Expor endpoints de status e versão da aplicação.
- ☐ Garantir métricas disponíveis para observabilidade.
- ☐ Disponibilizar configuração operacional via endpoint (`/config`).

## ☐ Fase 3 — Testes Orquestrados e Operações de Lab
**Objetivo:** Permitir testes de carga/soak com endpoints `/tests` e `/queue/purge` (atrás de feature flag).

- ☐ Criar endpoints de orquestração de testes (`/tests`).
- ☐ Implementar endpoint `/queue/purge` protegido por feature flag.
- ☐ Definir estratégias de carga/soak e observabilidade associada.

## ☐ Fase 4 — Segurança (AuthN / AuthZ / Hardening)
**Objetivo:** Adicionar OAuth2 Resource Server (JWT) e RBAC/ABAC.

- ☐ Configurar OAuth2 Resource Server com suporte a JWT.
- ☐ Implementar políticas de autorização RBAC/ABAC.
- ☐ Endurecer a aplicação (headers, TLS, configurações seguras).

## Regras de Execução e Qualidade
1. Trabalhar fase por fase.
2. Ao finalizar: marcar título com ✅, ticar checkboxes, e commitar com: `feat(phase): concluída Fase X - <descrição breve>`.
3. Swagger deve estar funcional desde a Fase 0.
4. Código sempre seguindo Clean Architecture + SOLID.
5. Sempre usar IBM MQ AllClient.
6. Todos os builds e testes devem estar verdes.
