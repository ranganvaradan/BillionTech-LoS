package com.los.core.creditintelligence.decisionpolicy;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycFactCatalog;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Honest section-level readiness for future Decision Policy implementability.
 * Does not invent readiness percentages — reports AVAILABLE / PARTIAL / UNKNOWN
 * from registry evidence only.
 */
public final class DecisionPolicySectionReadiness {

    private DecisionPolicySectionReadiness() {}

    public static List<Map<String, Object>> sectionRollup(PolicyAuthoringRegistry registry) {
        Map<String, Object> reg = registry == null ? Map.of() : registry.registry();
        List<Map<String, Object>> facts = listOfMaps(reg.get("facts"));
        List<Map<String, Object>> metrics = listOfMaps(reg.get("metrics"));

        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(kycEligibilitySection(facts));
        sections.add(section("CREDIT_UNDERWRITING", "Credit Underwriting",
                countAvailable(metrics, "bureau."), countAvailable(metrics, "banking.")
                        + countAvailable(metrics, "gst.") + countAvailable(metrics, "itr.")));
        sections.add(section("RISK_SCORE", "Risk / Score",
                countAvailable(metrics, "bureau.score"), 0));
        sections.add(section("LIMIT_PRICING", "Limit / Pricing", 0, 0));
        sections.add(section("DECISION_REVIEW", "Decision / Review", 0, 0));
        return sections;
    }

    public static List<Map<String, Object>> kycElementReadiness(PolicyAuthoringRegistry registry) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> fact : KycFactCatalog.registryEntries()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dataElementCode", fact.get("code"));
            row.put("businessName", fact.get("businessName"));
            row.put("category", "KYC & Eligibility");
            row.put("sourceFamily", fact.get("sourceFamily"));
            row.put("status", "AVAILABLE".equalsIgnoreCase(String.valueOf(fact.get("availability")))
                    ? "READY" : "NOT_AVAILABLE");
            row.put("automation", Boolean.TRUE.equals(fact.get("verificationRequirement"))
                    ? "AUTOMATABLE" : "MANUAL_OR_PRESENT");
            row.put("manualCapturePossible", fact.get("manualCapturePossible"));
            out.add(row);
        }
        for (String unsupported : KycFactCatalog.unsupportedCapabilityCodes()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dataElementCode", unsupported);
            row.put("businessName", unsupported);
            row.put("category", "KYC & Eligibility");
            row.put("status", "NOT_AVAILABLE");
            row.put("implementability", "BLOCKED");
            row.put("automation", "NOT_SUPPORTED");
            row.put("note", "Capability not proven by repository evidence — must not be marked AVAILABLE");
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> kycEligibilitySection(List<Map<String, Object>> facts) {
        long available = facts.stream()
                .filter(f -> String.valueOf(f.get("code")).startsWith("kyc."))
                .filter(f -> "AVAILABLE".equalsIgnoreCase(String.valueOf(f.get("availability"))))
                .count();
        long total = facts.stream()
                .filter(f -> String.valueOf(f.get("code")).startsWith("kyc."))
                .count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sectionCode", "KYC_ELIGIBILITY");
        m.put("sectionName", "KYC & Eligibility");
        m.put("supportedFactCount", available);
        m.put("registeredFactCount", total);
        m.put("readiness", available > 0 ? (available >= total ? "READY" : "PARTIAL") : "UNKNOWN");
        m.put("note", "Derived from registry evidence only — not a production authority rollup");
        m.put("allowCanonicalAuthority", false);
        return m;
    }

    private static Map<String, Object> section(String code, String name, long primaryAvailable, long secondary) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sectionCode", code);
        m.put("sectionName", name);
        long signal = primaryAvailable + secondary;
        m.put("supportedSignalCount", signal);
        m.put("readiness", signal > 0 ? "PARTIAL" : "UNKNOWN");
        m.put("note", "Honest signal count from registry — no invented percentage");
        m.put("allowCanonicalAuthority", false);
        return m;
    }

    private static long countAvailable(List<Map<String, Object>> rows, String prefix) {
        return rows.stream()
                .filter(r -> String.valueOf(r.get("code")).toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .filter(r -> "AVAILABLE".equalsIgnoreCase(String.valueOf(r.get("availability"))))
                .count();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }
}
