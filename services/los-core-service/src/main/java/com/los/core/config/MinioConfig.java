package com.los.core.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
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
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        initBucket(client, documentsBucket);
        initBucket(client, signedDocsBucket);

        return client;
    }

    private void initBucket(MinioClient client, String bucketName) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket '{}': {}", bucketName, e.getMessage());
        }
    }
}
