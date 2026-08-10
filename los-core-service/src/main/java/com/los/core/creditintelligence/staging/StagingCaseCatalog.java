package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CASE_A–E catalog for staging CEO review. All entries are validation fixtures.
 */
public final class StagingCaseCatalog {

    public static final String FIXTURE_BANNER = "VALIDATION FIXTURE — NOT REAL BORROWER DATA";

    private StagingCaseCatalog() {}

    public record CaseMeta(
            String caseCode,
            ValidationCaseCode enumCode,
            String title,
            String origin,
            String description
    ) {}

    private static final List<CaseMeta> CASES = List.of(
            new CaseMeta("CASE_A", ValidationCaseCode.CASE_A_STRONG,
                    "Strong multi-source alignment",
                    "REPRESENTATIVE_PROVIDER_FIXTURE",
                    "Aligned GST/ITR/Bank turnover; bureau EMI ≈ bank EMI"),
            new CaseMeta("CASE_B", ValidationCaseCode.CASE_B_LEGACY_DEFAULT,
                    "Legacy default dependent",
                    "REPRESENTATIVE_PROVIDER_FIXTURE",
                    "Silent legacy defaults drive scorecard where canonical data is thin"),
            new CaseMeta("CASE_C", ValidationCaseCode.CASE_C_TURNOVER_CONFLICT,
                    "Turnover conflict",
                    "REPRESENTATIVE_PROVIDER_FIXTURE",
                    "Material GST vs bank/ITR turnover variance"),
            new CaseMeta("CASE_D", ValidationCaseCode.CASE_D_OBLIGATION_CONFLICT,
                    "Obligation conflict",
                    "REPRESENTATIVE_PROVIDER_FIXTURE",
                    "Bureau EMI vs bank EMI mismatch"),
            new CaseMeta("CASE_E", ValidationCaseCode.CASE_E_INCOMPLETE,
                    "Incomplete sources",
                    "REPRESENTATIVE_PROVIDER_FIXTURE",
                    "Missing critical sources — DATA_INSUFFICIENT path")
    );

    public static List<CaseMeta> all() {
        return CASES;
    }

    public static CaseMeta require(String caseCode) {
        String key = normalize(caseCode);
        return CASES.stream()
                .filter(c -> c.caseCode().equals(key) || c.enumCode().name().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown staging case: " + caseCode));
    }

    public static String normalize(String caseCode) {
        if (caseCode == null || caseCode.isBlank()) {
            throw new IllegalArgumentException("caseCode required");
        }
        String u = caseCode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "A", "CASEA", "CASE_A_STRONG" -> "CASE_A";
            case "B", "CASEB", "CASE_B_LEGACY_DEFAULT" -> "CASE_B";
            case "C", "CASEC", "CASE_C_TURNOVER_CONFLICT" -> "CASE_C";
            case "D", "CASED", "CASE_D_OBLIGATION_CONFLICT" -> "CASE_D";
            case "E", "CASEE", "CASE_E_INCOMPLETE" -> "CASE_E";
            default -> u;
        };
    }

    public static Map<String, Object> listEntry(CaseMeta meta) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseCode", meta.caseCode());
        m.put("enumCode", meta.enumCode().name());
        m.put("title", meta.title());
        m.put("origin", meta.origin());
        m.put("description", meta.description());
        m.put("fixtureBanner", FIXTURE_BANNER);
        m.put("productionActive", false);
        m.put("authoritative", false);
        m.put("allowCanonicalAuthority", false);
        return m;
    }
}
