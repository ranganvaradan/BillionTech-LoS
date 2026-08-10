package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.evaluation.domain.CiReconciliationResultSet;
import com.los.core.creditintelligence.evaluation.repository.CiReconciliationResultSetRepository;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationResultRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReconciliationResultSetService {

    public static final String SCHEMA_VERSION = "RECON_RESULT_SET_V1";

    private final CiReconciliationResultRepository reconciliationResultRepository;
    private final CiReconciliationResultSetRepository reconciliationResultSetRepository;
    private final ContentHasher contentHasher;

    @Transactional
    public CiReconciliationResultSet pinLatestForApplication(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID metricResultSetId,
            List<String> reconciliationCodes) {
        List<UUID> ids = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        Map<String, String> definitionVersions = new LinkedHashMap<>();
        if (reconciliationCodes != null) {
            for (String code : reconciliationCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                reconciliationResultRepository
                        .findFirstByApplicationIdAndReconciliationCodeOrderByExecutedAtDesc(
                                applicationId, code.trim())
                        .ifPresent(r -> {
                            ids.add(r.getId());
                            codes.add(r.getReconciliationCode());
                            if (r.getDefinitionVersion() != null) {
                                definitionVersions.put(r.getReconciliationCode(), r.getDefinitionVersion());
                            }
                        });
            }
        }
        return persist(tenantId, applicationId, factSnapshotId, metricResultSetId, ids, codes, definitionVersions);
    }

    @Transactional
    public CiReconciliationResultSet pinFromIds(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID metricResultSetId,
            List<UUID> reconciliationResultIds) {
        List<UUID> ids = reconciliationResultIds != null
                ? new ArrayList<>(reconciliationResultIds)
                : new ArrayList<>();
        List<String> codes = new ArrayList<>();
        Map<String, String> definitionVersions = new LinkedHashMap<>();
        for (UUID id : ids) {
            reconciliationResultRepository.findById(id).ifPresent(r -> {
                codes.add(r.getReconciliationCode());
                if (r.getDefinitionVersion() != null && r.getReconciliationCode() != null) {
                    definitionVersions.put(r.getReconciliationCode(), r.getDefinitionVersion());
                }
            });
        }
        return persist(tenantId, applicationId, factSnapshotId, metricResultSetId, ids, codes, definitionVersions);
    }

    @Transactional(readOnly = true)
    public Map<String, CiReconciliationResult> lookupBySet(UUID setId) {
        CiReconciliationResultSet set = reconciliationResultSetRepository.findById(setId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation result set not found: " + setId));
        Map<String, CiReconciliationResult> out = new LinkedHashMap<>();
        List<UUID> ids = set.getReconciliationResultIds() != null
                ? set.getReconciliationResultIds()
                : List.of();
        for (UUID id : ids) {
            reconciliationResultRepository.findById(id).ifPresent(r -> {
                if (r.getReconciliationCode() != null) {
                    out.putIfAbsent(r.getReconciliationCode(), r);
                }
            });
        }
        return out;
    }

    private CiReconciliationResultSet persist(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID metricResultSetId,
            List<UUID> ids,
            List<String> codes,
            Map<String, String> definitionVersions) {
        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("reconciliationResultIds", ids.stream().map(UUID::toString).toList());
        hashPayload.put("reconciliationCodes", codes);
        hashPayload.put("definitionVersions", definitionVersions);
        hashPayload.put("factSnapshotId", factSnapshotId != null ? factSnapshotId.toString() : null);
        hashPayload.put("metricResultSetId", metricResultSetId != null ? metricResultSetId.toString() : null);
        String hash = contentHasher.hashMap(hashPayload);
        return reconciliationResultSetRepository.save(CiReconciliationResultSet.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .factSnapshotId(factSnapshotId)
                .reconciliationResultIds(List.copyOf(ids))
                .reconciliationCodes(List.copyOf(codes))
                .definitionVersions(Map.copyOf(definitionVersions))
                .metricResultSetId(metricResultSetId)
                .contentHash(hash)
                .schemaVersion(SCHEMA_VERSION)
                .build());
    }
}
