package br.com.orleon.mq.probe.infrastructure.persistence.dynamodb;

import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyRecord;
import br.com.orleon.mq.probe.domain.model.idempotency.IdempotencyStatus;
import br.com.orleon.mq.probe.domain.model.message.MessageOperationType;
import br.com.orleon.mq.probe.domain.ports.IdempotencyRepository;
import br.com.orleon.mq.probe.infrastructure.config.IdempotencyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DynamoDbIdempotencyRepository implements IdempotencyRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbIdempotencyRepository.class);
    private static final String PARTITION_KEY = "pk";
    private static final String STATUS_ATTRIBUTE = "status";
    private static final String REQUEST_HASH_ATTRIBUTE = "requestHash";
    private static final String RESPONSE_ATTRIBUTE = "responsePayload";
    private static final String CREATED_AT_ATTRIBUTE = "createdAt";
    private static final String UPDATED_AT_ATTRIBUTE = "updatedAt";
    private static final String EXPIRES_AT_ATTRIBUTE = "expiresAt";
    private static final String OPERATION_ATTRIBUTE = "operationType";
    private static final String IDEMPOTENCY_KEY_ATTRIBUTE = "idempotencyKey";
    private static final String TTL_ATTRIBUTE = "ttl";

    private final DynamoDbClient dynamoDbClient;
    private final IdempotencyProperties properties;

    public DynamoDbIdempotencyRepository(DynamoDbClient dynamoDbClient, IdempotencyProperties properties) {
        this.dynamoDbClient = dynamoDbClient;
        this.properties = properties;
    }

    @Override
    public Optional<IdempotencyRecord> find(MessageOperationType operationType, String idempotencyKey) {
        Map<String, AttributeValue> key = Map.of(PARTITION_KEY, AttributeValue.fromS(pk(operationType, idempotencyKey)));
        GetItemRequest request = GetItemRequest.builder()
                .tableName(properties.tableName())
                .key(key)
                .build();
        var response = dynamoDbClient.getItem(request);
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToRecord(response.item()));
    }

    @Override
    public boolean createInProgress(MessageOperationType operationType,
                                    String idempotencyKey,
                                    String requestHash,
                                    Instant createdAt,
                                    Instant expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PARTITION_KEY, AttributeValue.fromS(pk(operationType, idempotencyKey)));
        item.put(OPERATION_ATTRIBUTE, AttributeValue.fromS(operationType.name()));
        item.put(IDEMPOTENCY_KEY_ATTRIBUTE, AttributeValue.fromS(idempotencyKey));
        item.put(REQUEST_HASH_ATTRIBUTE, AttributeValue.fromS(requestHash));
        item.put(STATUS_ATTRIBUTE, AttributeValue.fromS(IdempotencyStatus.IN_PROGRESS.name()));
        item.put(CREATED_AT_ATTRIBUTE, AttributeValue.fromS(createdAt.toString()));
        item.put(UPDATED_AT_ATTRIBUTE, AttributeValue.fromS(createdAt.toString()));
        item.put(EXPIRES_AT_ATTRIBUTE, AttributeValue.fromS(expiresAt.toString()));
        item.put(TTL_ATTRIBUTE, AttributeValue.fromN(String.valueOf(expiresAt.getEpochSecond())));

        PutItemRequest request = PutItemRequest.builder()
                .tableName(properties.tableName())
                .item(item)
                .conditionExpression("attribute_not_exists(#pk)")
                .expressionAttributeNames(Map.of("#pk", PARTITION_KEY))
                .build();
        try {
            dynamoDbClient.putItem(request);
            return true;
        } catch (ConditionalCheckFailedException ex) {
            LOGGER.debug("Idempotency record already exists for {}", idempotencyKey);
            return false;
        }
    }

    @Override
    public void markCompleted(MessageOperationType operationType,
                              String idempotencyKey,
                              String responsePayload,
                              Instant updatedAt) {
        Map<String, AttributeValue> key = Map.of(PARTITION_KEY, AttributeValue.fromS(pk(operationType, idempotencyKey)));
        Map<String, String> names = Map.of(
                "#status", STATUS_ATTRIBUTE,
                "#updated", UPDATED_AT_ATTRIBUTE,
                "#response", RESPONSE_ATTRIBUTE
        );
        Map<String, AttributeValue> values = Map.of(
                ":status", AttributeValue.fromS(IdempotencyStatus.COMPLETED.name()),
                ":updated", AttributeValue.fromS(updatedAt.toString()),
                ":response", AttributeValue.fromS(responsePayload)
        );
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(properties.tableName())
                .key(key)
                .conditionExpression("attribute_exists(#pk)")
                .updateExpression("SET #status = :status, #updated = :updated, #response = :response")
                .expressionAttributeNames(merge(names, Map.of("#pk", PARTITION_KEY)))
                .expressionAttributeValues(values)
                .build();
        dynamoDbClient.updateItem(request);
    }

    @Override
    public void markFailed(MessageOperationType operationType,
                           String idempotencyKey,
                           Instant updatedAt,
                           IdempotencyStatus status) {
        Map<String, AttributeValue> key = Map.of(PARTITION_KEY, AttributeValue.fromS(pk(operationType, idempotencyKey)));
        Map<String, String> names = Map.of(
                "#status", STATUS_ATTRIBUTE,
                "#updated", UPDATED_AT_ATTRIBUTE
        );
        Map<String, AttributeValue> values = Map.of(
                ":status", AttributeValue.fromS(status.name()),
                ":updated", AttributeValue.fromS(updatedAt.toString())
        );
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(properties.tableName())
                .key(key)
                .conditionExpression("attribute_exists(#pk)")
                .updateExpression("SET #status = :status, #updated = :updated")
                .expressionAttributeNames(merge(names, Map.of("#pk", PARTITION_KEY)))
                .expressionAttributeValues(values)
                .build();
        dynamoDbClient.updateItem(request);
    }

    private String pk(MessageOperationType type, String idempotencyKey) {
        return type.name() + "#" + idempotencyKey;
    }

    private IdempotencyRecord mapToRecord(Map<String, AttributeValue> item) {
        MessageOperationType operationType = MessageOperationType.valueOf(item.get(OPERATION_ATTRIBUTE).s());
        String idempotencyKey = item.get(IDEMPOTENCY_KEY_ATTRIBUTE).s();
        String requestHash = item.containsKey(REQUEST_HASH_ATTRIBUTE) ? item.get(REQUEST_HASH_ATTRIBUTE).s() : null;
        IdempotencyStatus status = IdempotencyStatus.valueOf(item.get(STATUS_ATTRIBUTE).s());
        Instant createdAt = Instant.parse(item.get(CREATED_AT_ATTRIBUTE).s());
        Instant expiresAt = Instant.parse(item.get(EXPIRES_AT_ATTRIBUTE).s());
        Instant updatedAt = item.containsKey(UPDATED_AT_ATTRIBUTE) ? Instant.parse(item.get(UPDATED_AT_ATTRIBUTE).s()) : createdAt;
        String responsePayload = item.containsKey(RESPONSE_ATTRIBUTE) ? item.get(RESPONSE_ATTRIBUTE).s() : null;
        return new IdempotencyRecord(operationType, idempotencyKey, requestHash, status, createdAt, expiresAt, updatedAt, responsePayload);
    }

    private Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        Map<String, String> merged = new HashMap<>(first);
        merged.putAll(second);
        return merged;
    }
}
