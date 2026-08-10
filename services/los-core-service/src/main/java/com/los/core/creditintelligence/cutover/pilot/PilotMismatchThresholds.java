package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.domain.ReviewDisposition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pilot mismatch thresholds §12 — zero unexplained material / bug / more-permissive / replay fail.
 */
@Service
public class PilotMismatchThresholds {

    public record ThresholdResult(
            boolean pass,
            int unexplainedMaterial,
            int bugMismatches,
            int unexplainedMorePermissive,
            int replayFails,
            int criticalBindingFailures,
            List<String> blockers
    ) {
    }

    private static final Set<String> EXPLAINED = Set.of(
            ReviewDisposition.EXPECTED_CANONICAL.name(),
            ReviewDisposition.CANONICAL_CORRECT.name(),
            ReviewDisposition.LEGACY_CORRECT.name(),
            ReviewDisposition.POLICY_CHANGE_REQUIRED.name()
    );

    public ThresholdResult evaluate(List<CiCutoverComparison> comparisons, int replayFailCount) {
        List<CiCutoverComparison> rows = comparisons == null ? List.of() : comparisons;
        int unexplainedMaterial = 0;
        int bugs = 0;
        int unexplainedPermissive = 0;
        List<String> blockers = new ArrayList<>();

        for (CiCutoverComparison r : rows) {
            boolean explained = isExplained(r);
            String cls = r.getComparisonClass();
            String disposition = r.getReviewDisposition();

            if (ReviewDisposition.BUG.name().equals(disposition)
                    || (r.getRootCause() != null && r.getRootCause().contains("BUG"))) {
                bugs++;
            }
            boolean material = "MATERIAL".equals(r.getMateriality())
                    || (cls != null && cls.startsWith("MATERIAL_"));
            if (material && !explained) {
                unexplainedMaterial++;
            }
            if (ComparisonClass.CANONICAL_MORE_PERMISSIVE.name().equals(cls) && !explained) {
                unexplainedPermissive++;
            }
        }

        if (unexplainedMaterial > 0) {
            blockers.add("unexplained material mismatch = " + unexplainedMaterial + " (allowed 0)");
        }
        if (bugs > 0) {
            blockers.add("BUG-classified mismatch = " + bugs + " (allowed 0)");
        }
        if (unexplainedPermissive > 0) {
            blockers.add("unexplained canonical-more-permissive = " + unexplainedPermissive + " (allowed 0)");
        }
        if (replayFailCount > 0) {
            blockers.add("replay mismatch = " + replayFailCount + " (allowed 0)");
        }

        boolean pass = unexplainedMaterial == 0 && bugs == 0 && unexplainedPermissive == 0
                && replayFailCount == 0;
        return new ThresholdResult(pass, unexplainedMaterial, bugs, unexplainedPermissive,
                replayFailCount, 0, blockers);
    }

    private static boolean isExplained(CiCutoverComparison r) {
        if (r.getReviewDisposition() != null && EXPLAINED.contains(r.getReviewDisposition())) {
            return true;
        }
        return "REVIEWED".equalsIgnoreCase(r.getReviewStatus())
                || "CLOSED".equalsIgnoreCase(r.getReviewStatus());
    }

    public Map<String, Object> thresholdsDescriptor() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unexplainedMaterialMismatchAllowed", 0);
        out.put("bugMismatchAllowed", 0);
        out.put("canonicalMorePermissiveMissingGuardrailAllowed", 0);
        out.put("replayMismatchAllowed", 0);
        out.put("criticalBindingFailureAllowed", 0);
        return out;
    }
}
