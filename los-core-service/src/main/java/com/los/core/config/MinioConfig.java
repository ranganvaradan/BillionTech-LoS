package com.los.core.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "los.document.storage", havingValue = "minio")
public class MinioConfig {

    @Value("${los.minio.endpoint}")
    private String endpoint;

    @Value("${los.minio.access-key}")
    private String accessKey;

    @Value("${los.minio.secret-key}")
    private String secretKey;

    @Value("${los.minio.bucket-documents}")
    private String documentsBucket;

    @Value("${los.minio.bucket-signed-docs}")
    private String signedDocsBucket;

    @Bean
    public MinioClient minioClient() {
        String resolvedEndpoint = resolveEndpoint(endpoint);
        MinioClient client = MinioClient.builder()
                .endpoint(resolvedEndpoint)
                .credentials(accessKey, secretKey)
                .build();

        log.info("Initializing MinIO client with endpoint: {}", resolvedEndpoint);
        initBucket(client, documentsBucket);
        initBucket(client, signedDocsBucket);

        return client;
    }

    private String resolveEndpoint(String configured) {
        if (configured == null || configured.isBlank()) {
            return "http://127.0.0.1:9000";
        }
        String trimmed = configured.trim();
        // Avoid IPv6 localhost resolution issues on some hosts.
        if (trimmed.contains("://localhost")) {
            return trimmed.replace("://localhost", "://127.0.0.1");
        }
        return trimmed;
    }

    private void initBucket(MinioClient client, String bucketName) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket '{}' on endpoint '{}'", bucketName, endpoint, e);
        }
    }
}
