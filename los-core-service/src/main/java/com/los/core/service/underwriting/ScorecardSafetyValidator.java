package com.los.core.service.underwriting;

import com.los.core.model.entity.UnderwritingScorecard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SCORECARD-SAFETY-CLOSURE-1 — single validation path before activation.
 * Does not create a second scorecard engine.
 */
public final class ScorecardSafetyValidator {

    public static final String POLICY_REQUIRED = ScorecardSafetyScoring.MISSING_REQUIRED;
    public static final String POLICY_OPTIONAL_DEPRESS = ScorecardSafetyScoring.MISSING_OPTIONAL_DEPRESS;
    public static final String POLICY_OPTIONAL_SKIP = ScorecardSafetyScoring.MISSING_OPTIONAL_SKIP;

    private ScorecardSafetyValidator() {}

    public record ValidationResult(
            boolean ok,
            boolean bandsSafe,
            boolean denominatorSafe,
            boolean missingPoliciesExplicit,
            boolean missingPoliciesConfirmed,
            boolean hardRuleOperandsRequired,
            boolean thresholdsCoherent,
            List<String> problems,
            List<Map<String, Object>> factors) {}

    @SuppressWarnings("unchecked")
    public static ValidationResult validate(UnderwritingScorecard card) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> scj = card.getScorecardJson() != null ? card.getScorecardJson() : Map.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        Object rawRows = scj.get("rows");
        if (rawRows instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map) rows.add((Map<String, Object>) o);
            }
        }

        ScorecardExclusiveBandModel.Model model = ScorecardExclusiveBandModel.fromRows(rows);
        boolean bandsSafe = model.safe();
        if (!bandsSafe) {
            problems.addAll(model.problems());
        }

        boolean denominatorSafe = true;
        for (ScorecardExclusiveBandModel.FactorBands f : model.factors()) {
            if (!ScorecardExclusiveBandModel.MODE_EXCLUSIVE.equals(f.mode())) continue;
            int sum = f.bands().stream().mapToInt(ScorecardExclusiveBandModel.ExclusiveBand::score).sum();
            int max = f.maxPoints();
            if (f.bands().size() > 1 && sum != max && max != f.bands().stream()
                    .mapToInt(ScorecardExclusiveBandModel.ExclusiveBand::score).max().orElse(0)) {
                denominatorSafe = false;
                problems.add(f.parameter() + ": factorMaxPoints must be MAX(band scores), not SUM");
            }
            // invariant: factorMax == max(band scores)
            int expectedMax = f.bands().stream().mapToInt(ScorecardExclusiveBandModel.ExclusiveBand::score).max().orElse(0);
            if (max != expectedMax) {
                denominatorSafe = false;
                problems.add(f.parameter() + ": factorMaxPoints=" + max + " != MAX(bands)=" + expectedMax);
            }
        }

        Map<String, Object> safety = card.getSafetyJson() != null ? card.getSafetyJson() : Map.of();
        Map<String, Object> factorPolicies = safety.get("factorPolicies") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        boolean confirmed = Boolean.TRUE.equals(safety.get("missingDataPoliciesConfirmed"));

        Set<String> hardParams = hardRuleParameters(card);
        List<Map<String, Object>> factors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean allExplicit = true;
        boolean hardRequiredOk = true;

        for (ScorecardExclusiveBandModel.FactorBands f : model.factors()) {
            if (!seen.add(f.parameter() + "|" + Objects.toString(f.source(), ""))) continue;
            Map<String, Object> row = factorRow(f.parameter(), f.source(), f.maxPoints(),
                    hardParams.contains(f.parameter()), factorPolicies, f.mode());
            factors.add(row);
            if (!Boolean.TRUE.equals(row.get("explicit"))) {
                allExplicit = false;
            }
            if (hardParams.contains(f.parameter())
                    && !POLICY_REQUIRED.equals(String.valueOf(row.get("effectiveMissingData")))) {
                // For activation we require hard-rule factors to be REQUIRED explicitly or via hard-rule default
                if (Boolean.TRUE.equals(row.get("explicit"))
                        && !POLICY_REQUIRED.equals(String.valueOf(row.get("configuredMissingData")))) {
                    hardRequiredOk = false;
                    problems.add(f.parameter() + ": hard-rule operand must be REQUIRED");
                }
            }
        }
        // legacy single / non-numeric rows
        for (Map<String, Object> r : rows) {
            String p = str(r.get("parameter"));
            String s = str(r.get("source"));
            if (p == null) continue;
            String key = p + "|" + Objects.toString(s, "");
            if (!seen.add(key)) continue;
            int score = r.get("score") instanceof Number n ? n.intValue() : 0;
            Map<String, Object> row = factorRow(p, s, score, hardParams.contains(p), factorPolicies, "LEGACY_SINGLE");
            factors.add(row);
            if (!Boolean.TRUE.equals(row.get("explicit"))) {
                allExplicit = false;
            }
            if (hardParams.contains(p)
                    && Boolean.TRUE.equals(row.get("explicit"))
                    && !POLICY_REQUIRED.equals(String.valueOf(row.get("configuredMissingData")))) {
                hardRequiredOk = false;
                problems.add(p + ": hard-rule operand must be REQUIRED");
            }
        }

        // hard-rule-only parameters (no soft rows)
        for (String hp : hardParams) {
            boolean covered = factors.stream().anyMatch(f -> hp.equals(f.get("factor")));
            if (!covered) {
                Map<String, Object> row = factorRow(hp, null, 0, true, factorPolicies, "HARD_RULE_ONLY");
                factors.add(row);
                if (!Boolean.TRUE.equals(row.get("explicit"))) {
                    allExplicit = false;
                }
            }
        }

        Map<String, Object> t = card.getThresholdsJson() != null ? card.getThresholdsJson() : Map.of();
        int approve = intOr(t.get("approveMinPercent"), 70);
        int manual = intOr(t.get("manualMinPercent"), 40);
        boolean thresholdsCoherent = approve >= 0 && manual >= 0 && approve <= 100 && manual <= 100 && approve >= manual;
        if (!thresholdsCoherent) {
            problems.add("thresholds incoherent: approveMinPercent=" + approve + " manualMinPercent=" + manual);
        }

        boolean ok = bandsSafe && denominatorSafe && hardRequiredOk && thresholdsCoherent;
        return new ValidationResult(
                ok, bandsSafe, denominatorSafe, allExplicit, confirmed, hardRequiredOk, thresholdsCoherent,
                problems, factors);
    }

    /** Activation of a new ACTIVE authority requires explicit + confirmed missing-data policies. */
    public static ValidationResult validateForActivation(UnderwritingScorecard card) {
        ValidationResult base = validate(card);
        List<String> problems = new ArrayList<>(base.problems());
        if (!base.missingPoliciesExplicit()) {
            problems.add("missing-data policy is not explicit for every factor; cannot activate");
        }
        if (!base.missingPoliciesConfirmed()) {
            problems.add("missingDataPoliciesConfirmed=false; confirm classifications before activation");
        }
        // hard-rule operands must be REQUIRED (explicit or effective)
        for (Map<String, Object> f : base.factors()) {
            if (Boolean.TRUE.equals(f.get("hardRule"))
                    && !POLICY_REQUIRED.equals(String.valueOf(f.get("effectiveMissingData")))) {
                problems.add(f.get("factor") + ": hard-rule operand effective policy must be REQUIRED");
            }
        }
        boolean ok = base.bandsSafe()
                && base.denominatorSafe()
                && base.thresholdsCoherent()
                && base.missingPoliciesExplicit()
                && base.missingPoliciesConfirmed()
                && problems.stream().noneMatch(p -> p.contains("hard-rule"));
        return new ValidationResult(
                ok,
                base.bandsSafe(),
                base.denominatorSafe(),
                base.missingPoliciesExplicit(),
                base.missingPoliciesConfirmed(),
                base.hardRuleOperandsRequired(),
                base.thresholdsCoherent(),
                problems,
                base.factors());
    }

    /**
     * Build inherited factorPolicies for a DRAFT cloned from legacy ACTIVE.
     * Does not activate; confirmed remains false until human confirms.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> inheritedSafetyForNewVersion(UnderwritingScorecard source) {
        ValidationResult v = validate(source);
        Map<String, Object> factorPolicies = new LinkedHashMap<>();
        for (Map<String, Object> f : v.factors()) {
            String param = String.valueOf(f.get("factor"));
            String recommended = String.valueOf(f.get("recommendedMissingData"));
            Map<String, Object> pol = new LinkedHashMap<>();
            pol.put("missingData", recommended);
            pol.put("source", Boolean.TRUE.equals(f.get("hardRule"))
                    ? "INHERITED_HARD_RULE_REQUIRED"
                    : "INHERITED_LEGACY_OPTIONAL_DEPRESS");
            pol.put("needsHumanConfirmation", !Boolean.TRUE.equals(f.get("hardRule")));
            factorPolicies.put(param, pol);
        }
        Map<String, Object> safety = new LinkedHashMap<>(
                source.getSafetyJson() == null ? Map.of() : source.getSafetyJson());
        safety.put("factorPolicies", factorPolicies);
        safety.put("missingDataPoliciesExplicit", true);
        safety.put("missingDataPoliciesConfirmed", false);
        safety.put("weightSemantics", "METADATA_ONLY_NOT_USED_IN_FORMULA");
        safety.put("bandSemantics", ScorecardExclusiveBandModel.MODE_EXCLUSIVE);
        safety.put("denominatorSemantics", "SUM_OF_FACTOR_MAX_WHERE_FACTOR_MAX_IS_MAX_BAND_POINTS");
        safety.put("legacyInheritedMissingPolicies", true);
        return safety;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> factorRow(
            String parameter,
            String source,
            int factorMax,
            boolean hardRule,
            Map<String, Object> factorPolicies,
            String bandMode) {
        Object pol = factorPolicies.get(parameter);
        String configured = null;
        boolean explicit = false;
        if (pol instanceof Map<?, ?> m && m.get("missingData") != null) {
            configured = String.valueOf(m.get("missingData")).trim().toUpperCase(Locale.ROOT);
            explicit = POLICY_REQUIRED.equals(configured)
                    || POLICY_OPTIONAL_DEPRESS.equals(configured)
                    || POLICY_OPTIONAL_SKIP.equals(configured);
        }
        String recommended = hardRule ? POLICY_REQUIRED : POLICY_OPTIONAL_DEPRESS;
        String effective = explicit ? configured : (hardRule ? POLICY_REQUIRED : POLICY_OPTIONAL_DEPRESS);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("factor", parameter);
        row.put("source", source);
        row.put("factorMaxPoints", factorMax);
        row.put("bandMode", bandMode);
        row.put("hardRule", hardRule);
        row.put("explicit", explicit);
        row.put("configuredMissingData", configured);
        row.put("effectiveMissingData", effective);
        row.put("recommendedMissingData", recommended);
        row.put("implicitLegacy", !explicit);
        row.put("humanDecisionRequired", !explicit && !hardRule);
        return row;
    }

    private static Set<String> hardRuleParameters(UnderwritingScorecard card) {
        Set<String> out = new LinkedHashSet<>();
        Map<String, Object> hardWrap = card.getHardRulesJson() != null ? card.getHardRulesJson() : Map.of();
        Object h = hardWrap.get("rules");
        if (h instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("parameter") != null) {
                    out.add(String.valueOf(m.get("parameter")).trim());
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static int intOr(Object o, int d) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return d;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return d;
        }
    }
}
