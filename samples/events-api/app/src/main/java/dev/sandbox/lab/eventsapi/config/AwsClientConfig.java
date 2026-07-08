package dev.sandbox.lab.eventsapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

// Same story as catalog-api's DynamoDbConfig: no endpoint/region/credentials here - the AWS SDK
// for Java v2 reads AWS_ENDPOINT_URL/AWS_REGION/AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY straight
// from the environment.
@Configuration
public class AwsClientConfig {
    @Bean
    public SnsClient snsClient() {
        return SnsClient.create();
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.create();
    }
}
