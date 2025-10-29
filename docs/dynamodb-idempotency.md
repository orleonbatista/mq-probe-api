# Tabela `mq-probe-idempotency`

A tabela `mq-probe-idempotency` garante idempotência das operações de produção e consumo de mensagens. Ela deve ser criada manualmente no DynamoDB antes da execução da API.

## Especificação Lógica

| Atributo                | Tipo     | Descrição                                                                 |
|-------------------------|----------|---------------------------------------------------------------------------|
| `pk`                    | STRING   | Partition Key no formato `<operationType>#<idempotencyKey>`               |
| `operationType`         | STRING   | Tipo da operação (`PRODUCE` ou `CONSUME`)                                 |
| `idempotencyKey`        | STRING   | Chave idempotente informada na requisição                                 |
| `status`                | STRING   | Estado da execução (`IN_PROGRESS`, `COMPLETED`, `FAILED`)                 |
| `requestHash`           | STRING   | Hash SHA-256 do payload da requisição                                     |
| `responsePayload`       | STRING   | JSON serializado com o resultado final da operação (quando concluída)     |
| `createdAt`             | STRING   | Timestamp ISO-8601 de criação do registro                                 |
| `updatedAt`             | STRING   | Timestamp ISO-8601 da última atualização                                  |
| `expiresAt`             | STRING   | Timestamp ISO-8601 de expiração lógica                                    |
| `ttl`                   | NUMBER   | Epoch time em segundos usado pelo TTL do DynamoDB                         |

### Chaves e Provisionamento

- **Partition key:** `pk`
- **Billing mode:** `PAY_PER_REQUEST`
- **Time To Live (TTL):** habilitar sobre o atributo `ttl`
- **Global Secondary Indexes:** não são necessários nesta fase

### Fluxo de Escrita

1. Calcular o hash (`requestHash`) a partir do payload completo da requisição.
2. Executar `PutItem` condicional (`attribute_not_exists(pk)`) definindo `status = IN_PROGRESS` e o TTL desejado.
3. Após concluir a operação no IBM MQ, atualizar o item com `status = COMPLETED`, `responsePayload` e `updatedAt`.
4. Em caso de falha, atualizar o item com `status = FAILED` e `updatedAt`.
5. Novas requisições com o mesmo `idempotencyKey` retornam o cache quando `status = COMPLETED`; se `IN_PROGRESS`, retornam `409 Conflict`.

### Exemplo de Item

```json
{
  "pk": "PRODUCE#5c1e0a56-4a58-4aaf-9359-83ec8f663f10",
  "operationType": "PRODUCE",
  "idempotencyKey": "5c1e0a56-4a58-4aaf-9359-83ec8f663f10",
  "status": "COMPLETED",
  "requestHash": "f1c4b5a3...",
  "responsePayload": "{\"processedMessages\":100,\"elapsed\":\"PT2.5S\"}",
  "createdAt": "2025-10-29T12:00:00Z",
  "updatedAt": "2025-10-29T12:00:02Z",
  "expiresAt": "2025-10-30T12:00:00Z",
  "ttl": 1761748800
}
```

> **Observação:** o tempo de vida padrão (TTL) é configurado via propriedade `mqprobe.idempotency.record-ttl`, mas pode ser sobrescrito por requisição utilizando o header `Idempotency-Expiry-Seconds`.
