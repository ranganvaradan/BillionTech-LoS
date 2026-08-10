package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiSourceArtifact;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.repository.CiSourceArtifactRepository;
import com.los.core.creditintelligence.repository.CiSourceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal source registry for Phase F. Persists provenance references only —
 * never copies sensitive provider payloads into metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceRegistryService {

    private final CiSourceRecordRepository sourceRecordRepository;
    private final CiSourceArtifactRepository sourceArtifactRepository;
    private final CreditIntelligenceProperties properties;

    @Transactional
    public CiSourceRecord createOrGet(
            UUID tenantId,
            UUID applicationId,
            String sourceType,
            String providerCode,
            String purpose,
            String idempotencyKey,
            Map<String, Object> metadata,
            String createdBy) {
        UUID tid = tenantId != null ? tenantId : properties.getDefaultTenantId();
        Map<String, Object> safeMeta = sanitizeMetadata(metadata);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CiSourceRecord> existing = sourceRecordRepository
                    .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Prefer always persisting while building foundation snapshots (even if registry flag is off).
        CiSourceRecord record = CiSourceRecord.builder()
                .tenantId(tid)
                .applicationId(applicationId)
                .sourceType(sourceType)
                .providerCode(providerCode != null ? providerCode : "UNKNOWN")
                .purpose(purpose != null ? purpose : "UNDERWRITING")
                .status("CAPTURED")
                .idempotencyKey(idempotencyKey)
                .createdBy(createdBy)
                .metadata(safeMeta)
                .build();
        return sourceRecordRepository.save(record);
    }

    /**
     * Reference-only artifact row (checksum + content reference). Does not store sensitive content.
     */
    @Transactional
    public CiSourceArtifact createArtifact(
            UUID sourceRecordId, String contentReference, String contentType, String checksum) {
        CiSourceRecord record = sourceRecordRepository.findById(sourceRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Source record not found: " + sourceRecordId));
        CiSourceArtifact artifact = CiSourceArtifact.builder()
                .sourceRecordId(record.getId())
                .contentReference(contentReference)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .checksum(checksum != null ? checksum : "")
                .immutable(true)
                .build();
        return sourceArtifactRepository.save(artifact);
    }

    @Transactional(readOnly = true)
    public Optional<CiSourceRecord> findByIdForTenant(UUID tenantId, UUID sourceRecordId) {
        return sourceRecordRepository.findById(sourceRecordId)
                .filter(r -> r.getTenantId().equals(tenantId));
    }

    @Transactional(readOnly = true)
    public List<CiSourceRecord> listForApplication(UUID tenantId, UUID applicationId) {
        return sourceRecordRepository.findByTenantIdAndApplicationId(tenantId, applicationId);
    }

    /**
     * Strip keys that may contain PAN/Aadhaar/raw provider payloads before persistence or logging.
     */
    public static Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : metadata.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String keyLower = e.getKey().toLowerCase(Locale.ROOT);
            if (keyLower.contains("pan")
                    || keyLower.contains("aadhaar")
                    || keyLower.contains("aadhar")
                    || keyLower.contains("raw")
                    || keyLower.contains("payload")
                    || keyLower.contains("responsebody")) {
                continue;
            }
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                out.put(e.getKey(), sanitizeMetadata(nestedMap));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }
}
