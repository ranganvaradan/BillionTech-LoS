package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.CiCutoverCohort;
import com.los.core.creditintelligence.cutover.domain.CiCutoverDimension;
import com.los.core.creditintelligence.cutover.domain.CohortStatus;
import com.los.core.creditintelligence.cutover.domain.CutoverDimensionCode;
import com.los.core.creditintelligence.cutover.domain.DimensionReadiness;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cohort CRUD-ish. Refuses ACTIVE status and CANONICAL authority in G0.
 */
@Service
public class CutoverCohortService {

    private final CutoverStore store;

    public CutoverCohortService(CutoverStore store) {
        this.store = store;
    }

    public List<CiCutoverCohort> listAll() {
        return store.listCohorts();
    }

    public List<CiCutoverCohort> listByTenant(UUID tenantId) {
        return store.listCohortsByTenant(tenantId);
    }

    public CiCutoverCohort get(UUID id) {
        return store.findCohort(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cohort not found"));
    }

    public CiCutoverCohort create(CiCutoverCohort cohort) {
        if (cohort.getStatus() != null
                && CohortStatus.ACTIVE.name().equalsIgnoreCase(cohort.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "G0 refuses ACTIVE cohort status — use DRAFT/VALIDATION/DUAL_RUN/READY");
        }
        if (cohort.getMetadata() != null) {
            Object mode = cohort.getMetadata().get("authorityMode");
            if (mode != null && AuthorityMode.CANONICAL.name().equalsIgnoreCase(String.valueOf(mode))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "G0 refuses CANONICAL authorityMode");
            }
        }
        if (cohort.getStatus() == null) {
            cohort.setStatus(CohortStatus.DRAFT.name());
        }
        if (cohort.getCreatedAt() == null) {
            cohort.setCreatedAt(Instant.now());
        }
        if (cohort.getRollbackPolicy() == null || cohort.getRollbackPolicy().isEmpty()) {
            cohort.setRollbackPolicy(Map.of(
                    "killSwitch", "LEGACY",
                    "retainComparisons", true,
                    "redeployNotRequired", true));
        }
        return store.saveCohort(cohort);
    }

    public CiCutoverCohort updateStatus(UUID id, CohortStatus status) {
        if (status == CohortStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "G0 refuses ACTIVE cohort status");
        }
        CiCutoverCohort c = get(id);
        c.setStatus(status.name());
        return store.saveCohort(c);
    }

    public CiCutoverDimension upsertDimension(
            UUID cohortId, CutoverDimensionCode code, DimensionReadiness readiness,
            String authoritySource, String notes) {
        get(cohortId);
        if (AuthorityMode.CANONICAL.name().equalsIgnoreCase(authoritySource)
                && code == CutoverDimensionCode.AUTHORITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "G0 refuses CANONICAL authority dimension");
        }
        CiCutoverDimension existing = store.listDimensions(cohortId).stream()
                .filter(d -> code.name().equals(d.getDimensionCode()))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            existing = CiCutoverDimension.builder()
                    .cohortId(cohortId)
                    .dimensionCode(code.name())
                    .build();
        }
        existing.setReadiness(readiness.name());
        existing.setAuthoritySource(authoritySource != null ? authoritySource : "LEGACY");
        existing.setNotes(notes);
        return store.saveDimension(existing);
    }

    public List<CiCutoverDimension> dimensions(UUID cohortId) {
        get(cohortId);
        return store.listDimensions(cohortId);
    }

    public Map<String, Object> dimensionMatrix(UUID cohortId) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (CiCutoverDimension d : dimensions(cohortId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("readiness", d.getReadiness());
            row.put("authoritySource", d.getAuthoritySource());
            row.put("notes", d.getNotes());
            out.put(d.getDimensionCode(), row);
        }
        return out;
    }
}
