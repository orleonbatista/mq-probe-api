package br.com.orleon.mq.probe.infrastructure.config;

import br.com.orleon.mq.probe.application.service.IdempotencyService;
import br.com.orleon.mq.probe.domain.ports.IdempotencyRepository;
import br.com.orleon.mq.probe.infrastructure.persistence.dynamodb.DynamoDbIdempotencyRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class DynamoDbConfiguration {

    @Bean
    public DynamoDbClient dynamoDbClient(IdempotencyProperties properties) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        if (properties.region() != null) {
            builder.region(Region.of(properties.region()));
        }
        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointOverride()));
        }
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy"));
        builder.credentialsProvider(credentialsProvider);
        return builder.build();
    }

    @Bean
    public IdempotencyRepository idempotencyRepository(DynamoDbClient dynamoDbClient,
                                                       IdempotencyProperties properties) {
        return new DynamoDbIdempotencyRepository(dynamoDbClient, properties);
    }

    @Bean
    public IdempotencyService idempotencyService(IdempotencyRepository repository,
                                                 Clock clock,
                                                 IdempotencyProperties properties) {
        return new IdempotencyService(repository, clock, properties.recordTtl());
    }
}
