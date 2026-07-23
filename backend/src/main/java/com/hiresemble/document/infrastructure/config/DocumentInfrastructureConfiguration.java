package com.hiresemble.document.infrastructure.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    ObjectStorageProperties.class,
    DocumentParserProperties.class,
    ObjectDeletionOutboxProperties.class,
    DocumentEmbeddingProperties.class,
    DocumentAiCostProperties.class
})
public class DocumentInfrastructureConfiguration {

    @Bean
    S3Client documentS3Client(ObjectStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(credentials(properties))
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    S3Presigner documentS3Presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(credentials(properties))
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private StaticCredentialsProvider credentials(ObjectStorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey()));
    }
}
