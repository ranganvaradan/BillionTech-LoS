package com.los.core.service.underwriting;

import com.los.core.model.entity.UnderwritingScorecard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCORECARD-SAFETY-FOUNDATION-1 — inventory / validation report for persisted scorecards.
 * Does not rewrite unsafe scorecards.
 */
public final class ScorecardSafetyInventory {

    private ScorecardSafetyInventory() {}

    public record Report(
            int total,
            int active,
            int withOverlappingOrAmbiguousBands,
            int withGapsReported,
            int withDuplicateBands,
            int usingWeightMetadata,
            int usingComputed,
            int usingManualSources,
            int failingSafetyValidation,
            List<Map<String, Object>> unsafeDetails) {}

    @SuppressWarnings("unchecked")
    public static Report analyse(List<UnderwritingScorecard> cards) {
        int total = cards.size();
        int active = 0;
        int ambiguous = 0;
        int gaps = 0;
        int duplicates = 0;
        int weight = 0;
        int computed = 0;
        int manual = 0;
        int failing = 0;
        List<Map<String, Object>> unsafe = new ArrayList<>();

        for (UnderwritingScorecard c : cards) {
            if (c.isActive()) active++;
            Map<String, Object> scj = c.getScorecardJson() != null ? c.getScorecardJson() : Map.of();
            List<Map<String, Object>> rows = new ArrayList<>();
            Object rawRows = scj.get("rows");
            if (rawRows instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map) rows.add((Map<String, Object>) o);
                }
            }
            boolean hasWeight = false;
            boolean hasComputed = false;
            boolean hasManual = false;
            for (Map<String, Object> r : rows) {
                Object w = r.get("weight");
                if (w instanceof Number n && n.intValue() != 0) hasWeight = true;
                String src = r.get("source") == null ? "" : String.valueOf(r.get("source"));
                if ("COMPUTED".equalsIgnoreCase(src)) hasComputed = true;
                if (src.toUpperCase().contains("MANUAL")) hasManual = true;
            }
            if (hasWeight) weight++;
            if (hasComputed) computed++;
            if (hasManual) manual++;

            ScorecardExclusiveBandModel.Model model = ScorecardExclusiveBandModel.fromRows(rows);
            boolean dup = model.problems().stream().anyMatch(p -> p.contains("duplicate"));
            boolean gap = model.problems().stream().anyMatch(p -> p.toLowerCase().contains("gap"));
            if (dup) duplicates++;
            if (gap) gaps++;
            if (!model.safe()) {
                ambiguous++;
                failing++;
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("scorecardId", c.getId() == null ? null : c.getId().toString());
                detail.put("name", c.getName());
                detail.put("version", c.getVersion());
                detail.put("status", c.getStatus());
                detail.put("active", c.isActive());
                detail.put("problem", "AMBIGUOUS_OR_UNSAFE_BANDS");
                detail.put("problems", model.problems());
                detail.put("affectedFactors", model.factors().stream()
                        .filter(f -> ScorecardExclusiveBandModel.MODE_AMBIGUOUS.equals(f.mode()))
                        .map(ScorecardExclusiveBandModel.FactorBands::parameter)
                        .toList());
                detail.put("safeRemediation",
                        "Create DRAFT new version; rewrite overlapping bands as exclusive ranges or remove ambiguous rows. Do not edit ACTIVE in place.");
                unsafe.add(detail);
            }
        }
        return new Report(total, active, ambiguous, gaps, duplicates, weight, computed, manual, failing, unsafe);
    }
}
