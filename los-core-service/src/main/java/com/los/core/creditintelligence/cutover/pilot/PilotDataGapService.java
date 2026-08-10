package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.CiPilotDataGap;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists CiPilotDataGap for canonical DI cases (§15).
 */
@Service
public class PilotDataGapService {

    private final CutoverStore store;

    public PilotDataGapService(CutoverStore store) {
        this.store = store;
    }

    public List<CiPilotDataGap> captureFromComparisons(UUID cohortId) {
        List<CiPilotDataGap> gaps = new ArrayList<>();
        for (CiCutoverComparison r : store.listComparisons(cohortId)) {
            if (!ComparisonClass.CANONICAL_DATA_INSUFFICIENT.name().equals(r.getComparisonClass())
                    && !"DATA_INSUFFICIENT".equalsIgnoreCase(r.getCanonicalPolicyOutcome())) {
                continue;
            }
            String path = "unknown";
            String cause = "CANONICAL_DATA_INSUFFICIENT";
            if (r.getDecisionTrace() != null) {
                if (r.getDecisionTrace().get("canonicalPath") != null) {
                    path = String.valueOf(r.getDecisionTrace().get("canonicalPath"));
                }
                if (r.getDecisionTrace().get("diCause") != null) {
                    cause = String.valueOf(r.getDecisionTrace().get("diCause"));
                }
            }
            CiPilotDataGap gap = CiPilotDataGap.builder()
                    .applicationId(r.getApplicationId())
                    .cohortId(cohortId)
                    .canonicalPath(path)
                    .cause(cause)
                    .remediable(true)
                    .requiredAction("Remediate missing source / parser / metric gap")
                    .createdAt(Instant.now())
                    .build();
            gaps.add(store.saveDataGap(gap));
        }
        return gaps;
    }

    public CiPilotDataGap record(
            UUID cohortId,
            UUID applicationId,
            String canonicalPath,
            String cause,
            boolean remediable,
            String requiredAction) {
        return store.saveDataGap(CiPilotDataGap.builder()
                .cohortId(cohortId)
                .applicationId(applicationId)
                .canonicalPath(canonicalPath)
                .cause(cause)
                .remediable(remediable)
                .requiredAction(requiredAction)
                .createdAt(Instant.now())
                .build());
    }

    public Map<String, Object> frequencyByPath(UUID cohortId) {
        Map<String, Long> byPath = new LinkedHashMap<>();
        for (CiPilotDataGap g : store.listDataGaps(cohortId)) {
            byPath.merge(g.getCanonicalPath(), 1L, Long::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byPath", byPath);
        out.put("total", store.listDataGaps(cohortId).size());
        return out;
    }
}
