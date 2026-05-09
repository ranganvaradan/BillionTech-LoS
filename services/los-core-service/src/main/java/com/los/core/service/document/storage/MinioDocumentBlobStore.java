package com.los.core.service.document.storage;

import io.minio.GetObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "los.document.storage", havingValue = "minio")
public class MinioDocumentBlobStore implements DocumentBlobStore {

    private final MinioClient minioClient;

    @Value("${los.minio.bucket-documents}")
    private String bucketName;

    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created missing documents bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("Failed ensuring documents bucket '{}'", bucketName, e);
        }
    }

    @Override
    public void putObject(String key, byte[] data, long size, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(key)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build());
    }

    @Override
    public InputStream getObject(String key) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(key)
                .build());
    }

    @Override
    public void removeObject(String key) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(key)
                .build());
    }
}
