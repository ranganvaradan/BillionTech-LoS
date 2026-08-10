package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.core.clock.EvaluationClock;
import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import com.los.core.creditintelligence.evaluation.repository.CiEvaluationContextRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvaluationContextFactory {

    public static final String SCHEMA_VERSION = "EVALUATION_CONTEXT_V1";

    private final CiEvaluationContextRepository evaluationContextRepository;
    private final ContentHasher contentHasher;

    @Transactional
    public CiEvaluationContext create(
            UUID tenantId,
            UUID applicationId,
            UUID snapshotId,
            UUID policyVersionId,
            UUID configFreezeId,
            LocalDate asOf,
            EvaluationClock clock,
            UUID metricSetId,
            UUID reconSetId,
            Map<String, Object> metadata) {
        Map<String, Object> meta = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("tenantId", tenantId != null ? tenantId.toString() : null);
        hashPayload.put("applicationId", applicationId != null ? applicationId.toString() : null);
        hashPayload.put("factSnapshotId", snapshotId != null ? snapshotId.toString() : null);
        hashPayload.put("policyVersionId", policyVersionId != null ? policyVersionId.toString() : null);
        hashPayload.put("configFreezeId", configFreezeId != null ? configFreezeId.toString() : null);
        hashPayload.put("evaluationAsOf", asOf != null ? asOf.toString() : null);
        hashPayload.put("clockInstant", clock != null ? clock.instant().toString() : null);
        hashPayload.put("clockZone", clock != null ? clock.zone().getId() : "Asia/Kolkata");
        hashPayload.put("metricResultSetId", metricSetId != null ? metricSetId.toString() : null);
        hashPayload.put("reconciliationResultSetId", reconSetId != null ? reconSetId.toString() : null);
        hashPayload.put("schemaVersion", SCHEMA_VERSION);
        hashPayload.put("metadata", meta);

        String contentHash = contentHasher.hashMap(hashPayload);

        return evaluationContextRepository
                .findByTenantIdAndApplicationIdAndContentHash(tenantId, applicationId, contentHash)
                .orElseGet(() -> evaluationContextRepository.save(CiEvaluationContext.builder()
                        .tenantId(tenantId)
                        .applicationId(applicationId)
                        .factSnapshotId(snapshotId)
                        .policyVersionId(policyVersionId)
                        .configFreezeId(configFreezeId)
                        .evaluationAsOf(asOf != null ? asOf : (clock != null ? clock.today() : LocalDate.now()))
                        .clockInstant(clock != null ? clock.instant() : java.time.Instant.now())
                        .clockZone(clock != null ? clock.zone().getId() : "Asia/Kolkata")
                        .metricResultSetId(metricSetId)
                        .reconciliationResultSetId(reconSetId)
                        .schemaVersion(SCHEMA_VERSION)
                        .contentHash(contentHash)
                        .metadata(meta)
                        .build()));
    }
}
