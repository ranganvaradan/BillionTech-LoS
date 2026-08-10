package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.creditintelligence.validation.domain.CiReplayManifest;
import com.los.core.creditintelligence.validation.model.ProviderStackResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds CreditDecisionReplayManifest from EvaluationContext hashes — no raw payloads.
 */
@Service
public class ReplayManifestService {

    public static final String SCHEMA_VERSION = "CREDIT_DECISION_REPLAY_MANIFEST_V1";

    private final ContentHasher contentHasher;

    public ReplayManifestService(ContentHasher contentHasher) {
        this.contentHasher = contentHasher != null ? contentHasher : new ContentHasher();
    }

    public ReplayManifestService() {
        this(new ContentHasher());
    }

    public CiReplayManifest build(
            UUID tenantId,
            UUID applicationId,
            UUID evaluationContextId,
            String factSnapshotHash,
            String policyVersionHash,
            String configFreezeHash,
            String metricResultSetHash,
            String reconciliationResultSetHash,
            String deterministicEvaluationHash,
            List<ProviderStackResult> providerResults) {
        Map<String, Object> parsers = new LinkedHashMap<>();
        Map<String, Object> normalizers = new LinkedHashMap<>();
        List<Map<String, Object>> refs = new ArrayList<>();
        if (providerResults != null) {
            for (ProviderStackResult p : providerResults) {
                parsers.put(p.provider(), p.parserVersion());
                normalizers.put(p.provider(), p.normalizerVersion());
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("provider", p.provider());
                ref.put("sourceType", p.sourceType());
                ref.put("origin", p.origin() != null ? p.origin().name() : null);
                ref.put("entityCount", p.entityCount());
                // no raw payload
                refs.add(ref);
            }
        }
        return CiReplayManifest.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .evaluationContextId(evaluationContextId)
                .factSnapshotHash(factSnapshotHash)
                .policyVersionHash(policyVersionHash)
                .configFreezeHash(configFreezeHash)
                .metricResultSetHash(metricResultSetHash)
                .reconciliationResultSetHash(reconciliationResultSetHash)
                .deterministicEvaluationHash(deterministicEvaluationHash)
                .parserVersions(parsers)
                .normalizerVersions(normalizers)
                .sourceReferences(refs)
                .schemaVersion(SCHEMA_VERSION)
                .metadata(Map.of("rawPayloadsIncluded", false))
                .build();
    }

    public String hashMap(Map<String, Object> content) {
        return contentHasher.hashMap(content != null ? content : Map.of());
    }
}
