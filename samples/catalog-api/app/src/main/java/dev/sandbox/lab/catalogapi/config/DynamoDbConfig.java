package dev.sandbox.lab.catalogapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

// No endpoint/region/credentials configured here on purpose - the AWS SDK for Java v2 reads
// AWS_ENDPOINT_URL, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, and AWS_DEFAULT_REGION straight
// from the environment, the same env vars deploy.sh already exports for Pulumi and the AWS
// CLI. So DynamoDbClient.create() just works against Floci with zero Floci-specific code here,
// and pointing this at real AWS later is a matter of changing environment variables, not code.
@Configuration
public class DynamoDbConfig {
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.create();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }
}
