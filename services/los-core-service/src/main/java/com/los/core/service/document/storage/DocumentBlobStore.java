package com.los.core.service.document.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Object storage for loan documents (MinIO in deployed env; local filesystem in profile {@code local} or tests).
 */
public interface DocumentBlobStore {

    void putObject(String key, byte[] data, long size, String contentType) throws Exception;

    InputStream getObject(String key) throws Exception;

    void removeObject(String key) throws Exception;

    /**
     * When the backing store is the local filesystem, probe MIME from stored bytes (e.g. {@link java.nio.file.Files#probeContentType}).
     * MinIO and other backends return empty.
     */
    default Optional<String> probeStoredContentType(String storageKey) {
        return Optional.empty();
    }
}
