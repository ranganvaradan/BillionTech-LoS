package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.evaluation.domain.CiMetricResultSet;
import com.los.core.creditintelligence.evaluation.repository.CiMetricResultSetRepository;
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
public class MetricResultSetService {

    public static final String SCHEMA_VERSION = "METRIC_RESULT_SET_V1";

    private final CiMetricResultRepository metricResultRepository;
    private final CiMetricResultSetRepository metricResultSetRepository;
    private final ContentHasher contentHasher;

    @Transactional
    public CiMetricResultSet pinLatestForApplication(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            List<String> metricCodes) {
        List<UUID> ids = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        if (metricCodes != null) {
            for (String code : metricCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                metricResultRepository
                        .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(applicationId, code.trim())
                        .ifPresent(m -> {
                            ids.add(m.getId());
                            codes.add(m.getMetricCode());
                        });
            }
        }
        return persist(tenantId, applicationId, factSnapshotId, ids, codes);
    }

    @Transactional
    public CiMetricResultSet pinFromIds(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            List<UUID> metricResultIds) {
        List<UUID> ids = metricResultIds != null ? new ArrayList<>(metricResultIds) : new ArrayList<>();
        List<String> codes = new ArrayList<>();
        if (!ids.isEmpty()) {
            Map<UUID, CiMetricResult> byId = new LinkedHashMap<>();
            for (CiMetricResult m : metricResultRepository.findByIdIn(ids)) {
                byId.put(m.getId(), m);
            }
            for (UUID id : ids) {
                CiMetricResult m = byId.get(id);
                if (m != null) {
                    codes.add(m.getMetricCode());
                }
            }
        }
        return persist(tenantId, applicationId, factSnapshotId, ids, codes);
    }

    @Transactional(readOnly = true)
    public Map<String, CiMetricResult> lookupBySet(UUID setId) {
        CiMetricResultSet set = metricResultSetRepository.findById(setId)
                .orElseThrow(() -> new IllegalArgumentException("Metric result set not found: " + setId));
        Map<String, CiMetricResult> out = new LinkedHashMap<>();
        List<UUID> ids = set.getMetricResultIds() != null ? set.getMetricResultIds() : List.of();
        if (ids.isEmpty()) {
            return out;
        }
        for (CiMetricResult m : metricResultRepository.findByIdIn(ids)) {
            if (m.getMetricCode() != null) {
                out.putIfAbsent(m.getMetricCode(), m);
            }
        }
        return out;
    }

    private CiMetricResultSet persist(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            List<UUID> ids,
            List<String> codes) {
        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("metricResultIds", ids.stream().map(UUID::toString).toList());
        hashPayload.put("metricCodes", codes);
        hashPayload.put("factSnapshotId", factSnapshotId != null ? factSnapshotId.toString() : null);
        String hash = contentHasher.hashMap(hashPayload);
        return metricResultSetRepository.save(CiMetricResultSet.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .factSnapshotId(factSnapshotId)
                .metricResultIds(List.copyOf(ids))
                .metricCodes(List.copyOf(codes))
                .contentHash(hash)
                .schemaVersion(SCHEMA_VERSION)
                .build());
    }
}
