package dev.sandbox.lab.attachmentsapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

// Same story as catalog-api's DynamoDbConfig: no endpoint/region/credentials configured here -
// the AWS SDK for Java v2 reads AWS_ENDPOINT_URL/AWS_REGION/AWS_ACCESS_KEY_ID/
// AWS_SECRET_ACCESS_KEY straight from the environment, the same env vars deploy.sh already
// exports for Pulumi and the AWS CLI. S3Client.create() just works against Floci with zero
// Floci-specific code here.
@Configuration
public class S3ClientConfig {
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                // Path-style addressing (http://endpoint/bucket-name/key) instead of the
                // virtual-hosted style AWS uses by default in production
                // (http://bucket-name.endpoint/key) - Floci's endpoint doesn't do subdomain
                // routing (the same limitation documented for API Gateway elsewhere in this
                // repo), so path-style is what actually works against it. Harmless against real
                // AWS too, just not the modern default there.
                .forcePathStyle(true)
                .build();
    }
}
