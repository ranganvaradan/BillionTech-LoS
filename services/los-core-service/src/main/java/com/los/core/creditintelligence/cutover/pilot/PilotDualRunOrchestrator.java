package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.CiCutoverOperationalEvent;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.service.CutoverDualRunService;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs dual-run for each validation case; isolates canonical failures; never blocks legacy (§10/§28).
 */
@Service
public class PilotDualRunOrchestrator {

    public record DualRunCase(
            UUID applicationId,
            String label,
            String dataOrigin,
            Map<String, Object> legacySnapshot,
            Map<String, Object> canonicalSnapshot,
            boolean legacyUsedDefault,
            boolean forceCanonicalFailure
    ) {
    }

    public record OrchestrationResult(
            int attempted,
            int persisted,
            int canonicalFailures,
            List<CiCutoverComparison> comparisons,
            List<String> notes
    ) {
    }

    private final CutoverStore store;
    private final CutoverDualRunService dualRunService;

    public PilotDualRunOrchestrator(CutoverStore store, CutoverDualRunService dualRunService) {
        this.store = store;
        this.dualRunService = dualRunService;
    }

    public OrchestrationResult runCases(UUID cohortId, List<DualRunCase> cases) {
        List<CiCutoverComparison> comparisons = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int failures = 0;
        int persisted = 0;
        List<DualRunCase> input = cases == null ? List.of() : cases;

        for (DualRunCase c : input) {
            try {
                Map<String, Object> canonical = c.canonicalSnapshot() == null
                        ? new LinkedHashMap<>() : new LinkedHashMap<>(c.canonicalSnapshot());
                Map<String, Object> trace = new LinkedHashMap<>();
                trace.put("dataOrigin", c.dataOrigin());
                trace.put("label", c.label());
                trace.put("productionAuthority", "LEGACY");
                trace.put("automaticDualRun", true);

                if (c.forceCanonicalFailure()) {
                    failures++;
                    canonical.put("outcome", "DATA_INSUFFICIENT");
                    canonical.put("policyOutcome", "DATA_INSUFFICIENT");
                    trace.put("comparisonOverride", ComparisonClass.CANONICAL_DATA_INSUFFICIENT.name());
                    trace.put("canonicalEvaluationStatus", "CANONICAL_EVALUATION_FAILED");
                    store.saveOperationalEvent(CiCutoverOperationalEvent.builder()
                            .cohortId(cohortId)
                            .eventType("CANONICAL_EVALUATION_FAILED")
                            .detail(Map.of(
                                    "applicationId", String.valueOf(c.applicationId()),
                                    "label", c.label() == null ? "" : c.label(),
                                    "sanitized", true))
                            .createdBy("pilot-dual-run")
                            .createdAt(Instant.now())
                            .build());
                    notes.add("CANONICAL_EVALUATION_FAILED isolated for " + c.label()
                            + " — legacy unaffected");
                }

                CiCutoverComparison row = dualRunService.compareSnapshots(
                        cohortId,
                        c.applicationId(),
                        UUID.randomUUID(),
                        c.legacySnapshot(),
                        canonical,
                        c.legacyUsedDefault(),
                        trace);
                if (c.forceCanonicalFailure() && row.getDecisionTrace() != null) {
                    row.getDecisionTrace().put("canonicalEvaluationStatus", "CANONICAL_EVALUATION_FAILED");
                    store.saveComparison(row);
                }
                comparisons.add(row);
                persisted++;
            } catch (Exception ex) {
                failures++;
                notes.add("Canonical path error isolated: " + ex.getClass().getSimpleName()
                        + " — legacy unaffected");
                store.saveOperationalEvent(CiCutoverOperationalEvent.builder()
                        .cohortId(cohortId)
                        .eventType("CANONICAL_EVALUATION_FAILED")
                        .detail(Map.of(
                                "error", ex.getClass().getSimpleName(),
                                "sanitized", true))
                        .createdBy("pilot-dual-run")
                        .createdAt(Instant.now())
                        .build());
            }
        }
        return new OrchestrationResult(input.size(), persisted, failures, comparisons, notes);
    }

    /** C6 CASE_A–E representative fixture applications for dual-run (honest labels). */
    public static List<DualRunCase> c6RepresentativeFixtures() {
        List<DualRunCase> cases = new ArrayList<>();
        String[] codes = {"CASE_A", "CASE_B", "CASE_C", "CASE_D", "CASE_E"};
        for (int i = 0; i < codes.length; i++) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("outcome", "PASS");
            legacy.put("policyOutcome", "PASS");
            legacy.put("amount", new BigDecimal("500000"));
            legacy.put("tenure", 12);
            legacy.put("pricing", new BigDecimal("14.5"));
            legacy.put("authority", "CREDIT_MANAGER_L1");
            legacy.put("conditions", List.of());

            Map<String, Object> canonical = new LinkedHashMap<>(legacy);
            if ("CASE_B".equals(codes[i])) {
                // legacy default dependent scenario
                legacy.put("outcome", "PASS");
                canonical.put("outcome", "REFER");
                canonical.put("policyOutcome", "REFER");
            }
            cases.add(new DualRunCase(
                    UUID.fromString(String.format("c6000000-0000-4000-8000-%012d", i + 1)),
                    codes[i],
                    "REPRESENTATIVE_FIXTURE",
                    legacy,
                    canonical,
                    "CASE_B".equals(codes[i]),
                    false));
        }
        return cases;
    }
}
