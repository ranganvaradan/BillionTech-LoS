package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.service.CutoverCamCompatibilityAdapter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CAM + underwriter UI readiness for dual-run (§25/§26).
 */
@Service
public class PilotCamUiReadinessAssessor {

    private final CutoverCamCompatibilityAdapter camAdapter;

    public PilotCamUiReadinessAssessor(CutoverCamCompatibilityAdapter camAdapter) {
        this.camAdapter = camAdapter != null ? camAdapter : new CutoverCamCompatibilityAdapter();
    }

    public PilotCamUiReadinessAssessor() {
        this(new CutoverCamCompatibilityAdapter());
    }

    public Map<String, Object> assess(CiCutoverComparison comparison) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (comparison == null) {
            out.put("available", false);
            return out;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("amount", comparison.getCanonicalAmount());
        fields.put("tenure", comparison.getCanonicalTenure());
        fields.put("pricing", comparison.getCanonicalPricing());
        fields.put("conditions", comparison.getCanonicalConditions());
        fields.put("authority", comparison.getCanonicalAuthority());
        fields.put("outcome", comparison.getCanonicalPolicyOutcome());
        fields.put("reasonCodes", comparison.getReasonCodes());

        Map<String, Object> cam = camAdapter.toCamReadModel(fields);
        List<String> mapped = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> manual = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();

        check(cam, "amount", mapped, missing);
        check(cam, "tenure", mapped, missing);
        check(cam, "pricing", mapped, missing);
        check(cam, "authorityLevel", mapped, missing);
        check(cam, "outcome", mapped, missing);
        if (cam.get("conditions") == null || (cam.get("conditions") instanceof List<?> l && l.isEmpty())) {
            manual.add("conditions");
        } else {
            mapped.add("conditions");
        }
        unsupported.add("productionCamWrite");

        out.put("cam", cam);
        out.put("mapped", mapped);
        out.put("missing", missing);
        out.put("manual", manual);
        out.put("unsupported", unsupported);

        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("legacy", Map.of(
                "policy", nullToDash(comparison.getLegacyPolicyOutcome()),
                "amount", comparison.getLegacyAmount(),
                "tenure", comparison.getLegacyTenure(),
                "pricing", comparison.getLegacyPricing(),
                "conditions", comparison.getLegacyConditions(),
                "authority", nullToDash(comparison.getLegacyAuthority())));
        ui.put("canonical", Map.of(
                "policy", nullToDash(comparison.getCanonicalPolicyOutcome()),
                "amount", comparison.getCanonicalAmount(),
                "tenure", comparison.getCanonicalTenure(),
                "pricing", comparison.getCanonicalPricing(),
                "conditions", comparison.getCanonicalConditions(),
                "authority", nullToDash(comparison.getCanonicalAuthority()),
                "reasonCodes", comparison.getReasonCodes()));
        ui.put("comparisonClass", comparison.getComparisonClass());
        ui.put("readOnly", true);
        out.put("dualRunUiReadModel", ui);
        out.put("writesCam", false);
        return out;
    }

    private static void check(Map<String, Object> cam, String key, List<String> mapped, List<String> missing) {
        if (cam.get(key) != null) mapped.add(key);
        else missing.add(key);
    }

    private static String nullToDash(String s) {
        return s == null ? "-" : s;
    }
}
