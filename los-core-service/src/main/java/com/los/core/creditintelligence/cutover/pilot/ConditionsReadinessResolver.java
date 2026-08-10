package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiCutoverDimension;
import com.los.core.creditintelligence.cutover.domain.CutoverDimensionCode;
import com.los.core.creditintelligence.cutover.domain.DimensionReadiness;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CONDITIONS must be explicitly LEGACY or certified mapping (§24). Prefer LEGACY for G0.1 if CAM incomplete.
 */
@Service
public class ConditionsReadinessResolver {

    public record ConditionsResolution(
            String readiness,
            String authoritySource,
            String note,
            boolean ambiguous
    ) {
    }

    private final CutoverStore store;

    public ConditionsReadinessResolver(CutoverStore store) {
        this.store = store;
    }

    public ConditionsResolution resolve(UUID cohortId, boolean camMappingComplete) {
        if (camMappingComplete) {
            updateDimension(cohortId, DimensionReadiness.READY, "CANONICAL",
                    "Conditions mapping certified to CAM/UI");
            return new ConditionsResolution(DimensionReadiness.READY.name(), "CANONICAL",
                    "Conditions mapping certified", false);
        }
        // Prefer LEGACY with explicit note for G0.1
        updateDimension(cohortId, DimensionReadiness.LEGACY, "LEGACY",
                "G0.1: CAM mapping incomplete — CONDITIONS retained LEGACY explicitly");
        return new ConditionsResolution(DimensionReadiness.LEGACY.name(), "LEGACY",
                "G0.1: CAM mapping incomplete — CONDITIONS retained LEGACY explicitly (not ambiguous)",
                false);
    }

    private void updateDimension(UUID cohortId, DimensionReadiness readiness, String auth, String notes) {
        CiCutoverDimension existing = store.listDimensions(cohortId).stream()
                .filter(d -> CutoverDimensionCode.CONDITIONS.name().equals(d.getDimensionCode()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setReadiness(readiness.name());
            existing.setAuthoritySource(auth);
            existing.setNotes(notes);
            store.saveDimension(existing);
        } else {
            store.saveDimension(CiCutoverDimension.builder()
                    .cohortId(cohortId)
                    .dimensionCode(CutoverDimensionCode.CONDITIONS.name())
                    .readiness(readiness.name())
                    .authoritySource(auth)
                    .notes(notes)
                    .build());
        }
    }

    public Map<String, Object> asMap(ConditionsResolution r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("readiness", r.readiness());
        out.put("authoritySource", r.authoritySource());
        out.put("note", r.note());
        out.put("ambiguous", r.ambiguous());
        return out;
    }
}
