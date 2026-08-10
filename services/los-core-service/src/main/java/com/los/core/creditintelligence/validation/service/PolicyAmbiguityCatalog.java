package com.los.core.creditintelligence.validation.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documents PolicyMappingCandidate structure and example ambiguous phrases for P0 Policy Studio.
 */
@Component
public class PolicyAmbiguityCatalog {

    public record PolicyMappingCandidate(
            String clauseId,
            String phrase,
            List<String> possibleCanonicalPaths,
            List<Double> confidence,
            String selectedPath,
            String selectionSource,
            String reviewer
    ) {
    }

    public Map<String, Object> catalog() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("structure", Map.of(
                "clauseId", "string",
                "phrase", "string",
                "possibleCanonicalPaths", "string[]",
                "confidence", "number[]",
                "selectedPath", "string|null",
                "selectionSource", "AI|HUMAN|RULE",
                "reviewer", "string|null"
        ));
        out.put("examples", List.of(
                candidate("C1", "Average monthly transactions",
                        List.of("metric:bank.avg_monthly_txn_count", "metric:bank.avg_monthly_txn_value"),
                        List.of(0.55, 0.45)),
                candidate("C2", "EDI",
                        List.of("metric:bank.edi_credits", "fact:bank.narration_taxonomy.EDI"),
                        List.of(0.40, 0.50)),
                candidate("C3", "Clean string",
                        List.of("metric:bureau.clean_string_months", "metric:bureau.dpd_zero_streak"),
                        List.of(0.60, 0.35)),
                candidate("C4", "large credits",
                        List.of("metric:bank.large_credit_count", "metric:bank.credit_gt_threshold_sum"),
                        List.of(0.50, 0.45)),
                candidate("C5", "bulk deposition",
                        List.of("metric:bank.bulk_deposit_count", "fact:bank.narration_taxonomy.BULK_DEPOSIT"),
                        List.of(0.55, 0.40))
        ));
        out.put("note", "Design-only for P0 AI Policy Studio — no AI invocation in C6");
        return out;
    }

    private static Map<String, Object> candidate(
            String id, String phrase, List<String> paths, List<Double> conf) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clauseId", id);
        m.put("phrase", phrase);
        m.put("possibleCanonicalPaths", paths);
        m.put("confidence", conf);
        m.put("selectedPath", null);
        m.put("selectionSource", null);
        m.put("reviewer", null);
        return m;
    }
}
