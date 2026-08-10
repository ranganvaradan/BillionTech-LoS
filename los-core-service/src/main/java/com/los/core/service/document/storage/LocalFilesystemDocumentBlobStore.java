package com.los.core.service.document.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Local demo / CI storage when MinIO is not running. Root directory is created on demand.
 */
@Component
@ConditionalOnProperty(name = "los.document.storage", havingValue = "local")
public class LocalFilesystemDocumentBlobStore implements DocumentBlobStore {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemDocumentBlobStore.class);

    private final Path root;

    public LocalFilesystemDocumentBlobStore(
            @Value("${file.upload-dir:${los.file-storage.root:./uploads}}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
            if (!Files.isWritable(this.root)) {
                log.warn("Document storage root is not writable: {}", this.root);
            } else {
                log.info("Document storage root ready: {}", this.root);
            }
        } catch (Exception e) {
            log.warn("Could not create document storage root {}: {}", this.root, e.getMessage());
        }
    }

    @Override
    public void putObject(String key, byte[] data, long size, String contentType) throws Exception {
        Path p = resolveSafe(key);
        Files.createDirectories(p.getParent());
        Files.write(p, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public InputStream getObject(String key) throws Exception {
        Path p = resolveSafe(key);
        return new ByteArrayInputStream(Files.readAllBytes(p));
    }

    @Override
    public void removeObject(String key) throws Exception {
        Path p = resolveSafe(key);
        Files.deleteIfExists(p);
    }

    @Override
    public Optional<String> probeStoredContentType(String storageKey) {
        try {
            Path p = resolveSafe(storageKey);
            if (!Files.isRegularFile(p)) {
                return Optional.empty();
            }
            String probed = Files.probeContentType(p);
            if (probed == null || probed.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(probed);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Path resolveSafe(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new SecurityException("Key escapes storage root");
        }
        return p;
    }
}
