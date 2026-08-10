package com.los.core.creditintelligence.cutover.fixture;

import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.CiCutoverCohort;
import com.los.core.creditintelligence.cutover.domain.CiCutoverControl;
import com.los.core.creditintelligence.cutover.domain.CiCutoverDimension;
import com.los.core.creditintelligence.cutover.domain.CohortStatus;
import com.los.core.creditintelligence.cutover.domain.CutoverDimensionCode;
import com.los.core.creditintelligence.cutover.domain.DimensionReadiness;
import com.los.core.creditintelligence.cutover.store.CutoverStore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Narrow G0 candidate cohort fixture — DIGILEAP / SCF_STARTER validation scope.
 */
public final class G0CandidateCohortFactory {

    public static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID COHORT_ID = UUID.fromString("a1060000-0000-4000-8000-000000000001");
    public static final String PRODUCT_CODE = "DIGILEAP";
    public static final String LABEL = "G0_CANDIDATE_COHORT_V1";

    private G0CandidateCohortFactory() {
    }

    public static CiCutoverCohort seed(CutoverStore store) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("label", LABEL);
        metadata.put("productAliases", List.of("SCF_STARTER", "DIGILEAP"));
        metadata.put("authorityMode", AuthorityMode.LEGACY.name());

        CiCutoverCohort cohort = CiCutoverCohort.builder()
                .id(COHORT_ID)
                .tenantId(TENANT_ID)
                .productCode(PRODUCT_CODE)
                .segment("G0_NARROW_PILOT")
                .effectiveFrom(Instant.now())
                .status(CohortStatus.VALIDATION.name())
                .bindingSetVersion("G0_BINDING_SET_V1")
                .rollbackPolicy(Map.of(
                        "killSwitch", "LEGACY",
                        "retainComparisons", true,
                        "redeployNotRequired", true))
                .createdBy("system")
                .metadata(metadata)
                .createdAt(Instant.now())
                .build();
        store.saveCohort(cohort);

        seedDim(store, CutoverDimensionCode.SOURCE, DimensionReadiness.READY, "CANONICAL");
        seedDim(store, CutoverDimensionCode.FACT, DimensionReadiness.READY, "CANONICAL");
        seedDim(store, CutoverDimensionCode.METRIC, DimensionReadiness.READY, "CANONICAL");
        seedDim(store, CutoverDimensionCode.RECONCILIATION, DimensionReadiness.READY, "CANONICAL");
        seedDim(store, CutoverDimensionCode.POLICY, DimensionReadiness.READY, "CANONICAL");
        seedDim(store, CutoverDimensionCode.SCORECARD, DimensionReadiness.READY, "CANONICAL");
        seedDim(store, CutoverDimensionCode.LIMIT, DimensionReadiness.LEGACY, "LEGACY");
        seedDim(store, CutoverDimensionCode.PRICING, DimensionReadiness.LEGACY, "LEGACY");
        seedDim(store, CutoverDimensionCode.TENURE, DimensionReadiness.LEGACY, "LEGACY");
        seedDim(store, CutoverDimensionCode.COLLATERAL, DimensionReadiness.LEGACY, "LEGACY");
        seedDim(store, CutoverDimensionCode.AUTHORITY, DimensionReadiness.LEGACY, "LEGACY");
        seedDim(store, CutoverDimensionCode.CONDITIONS, DimensionReadiness.NOT_READY, "LEGACY");

        store.saveControl(CiCutoverControl.builder()
                .cohortId(COHORT_ID)
                .authorityMode(AuthorityMode.LEGACY.name())
                .effectiveAt(Instant.now())
                .changedBy("system")
                .reason("G0 fixture seed")
                .audit(Map.of("g0", true, "allowCanonical", false))
                .createdAt(Instant.now())
                .build());
        return cohort;
    }

    private static void seedDim(
            CutoverStore store, CutoverDimensionCode code, DimensionReadiness readiness, String auth) {
        store.saveDimension(CiCutoverDimension.builder()
                .cohortId(COHORT_ID)
                .dimensionCode(code.name())
                .readiness(readiness.name())
                .authoritySource(auth)
                .notes("G0 fixture")
                .build());
    }
}
