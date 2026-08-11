package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * SCORECARD-CONVERGENCE-1 — maps legacy scorecard parameter keys to GACAT canonical ids.
 * Does not invent mappings; EXACT uses liveScorecardParameter, SAFE_ALIAS is curated only.
 */
public final class ScorecardCanonicalFactorMapper {

    public static final String EXACT = "EXACT";
    public static final String SAFE_ALIAS = "SAFE_ALIAS";
    public static final String AMBIGUOUS = "AMBIGUOUS";
    public static final String NO_MATCH = "NO_MATCH";
    public static final String SEMANTIC_MISMATCH = "SEMANTIC_MISMATCH";
    public static final String LEGACY_CUSTOM = "LEGACY_CUSTOM";

    /** Curated SAFE_ALIAS only — documented semantic equivalents; runtime key unchanged. */
    private static final Map<String, String> SAFE_ALIASES = Map.of(
            "ANNUAL_GST_TURNOVER", "gst.turnover.trailing_12m",
            "REQUESTED_AMOUNT", "application.requested_amount",
            "BUSINESS_VINTAGE_MONTHS", "application.business_vintage_months",
            "CHEQUE_BOUNCES_3M", "banking.cheque_return_count_3m"
    );

    /** Known ambiguous — do not auto-bind. */
    private static final Map<String, String> AMBIGUOUS_REASONS = Map.ofEntries(
            Map.entry("MONTHLY_INCOME", "Could be declared income, bank-derived income, or GST-derived — no unique GACAT liveScorecardParameter"),
            Map.entry("BANK_STATEMENT_INCOME", "Overlaps with banking credits / declared income — no unique binding"),
            Map.entry("ITR_INCOME", "Multiple financial income heads in GACAT — no unique liveScorecardParameter"),
            Map.entry("PAT", "financial.pat exists as DERIVED_DEFINED_NOT_IMPLEMENTED in seed — not live scorecard-bound"),
            Map.entry("DSCR", "No GACAT liveScorecardParameter"),
            Map.entry("EBITDA_PROXY", "No GACAT liveScorecardParameter")
    );

    private ScorecardCanonicalFactorMapper() {}

    public record Binding(
            String legacyParameterKey,
            String canonicalParameterId,
            int canonicalDefinitionVersion,
            String mappingStatus,
            String sourceFamily,
            String businessName,
            String unit,
            String valueTypeHint,
            String availability,
            boolean productionReady,
            String reason) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("legacyParameterKey", legacyParameterKey);
            m.put("canonicalParameterId", canonicalParameterId);
            m.put("canonicalDefinitionVersion", canonicalDefinitionVersion);
            m.put("mappingStatus", mappingStatus);
            m.put("sourceFamily", sourceFamily);
            m.put("businessName", businessName);
            m.put("unit", unit);
            m.put("valueTypeHint", valueTypeHint);
            m.put("availability", availability);
            m.put("productionReady", productionReady);
            m.put("reason", reason);
            return m;
        }
    }

    public static Binding resolve(String legacyKey) {
        return resolve(legacyKey, CanonicalParameterRegistry.shared());
    }

    public static Binding resolve(String legacyKey, CanonicalParameterRegistry registry) {
        if (legacyKey == null || legacyKey.isBlank()) {
            return new Binding(null, null, 0, NO_MATCH, null, null, null, null, null, false, "empty key");
        }
        String key = legacyKey.trim();
        // EXACT: liveScorecardParameter match
        for (CanonicalParameterDefinition d : registry.all()) {
            if (d.liveScorecardParameter() != null
                    && d.liveScorecardParameter().equalsIgnoreCase(key)) {
                return binding(key, d, EXACT, 1, "liveScorecardParameter exact match");
            }
        }
        // liveRuleParameter exact for scorecard keys that used rule naming
        for (CanonicalParameterDefinition d : registry.all()) {
            if (d.liveRuleParameter() != null
                    && d.liveRuleParameter().equalsIgnoreCase(key)
                    && SAFE_ALIASES.containsKey(key.toUpperCase(Locale.ROOT))) {
                // only if also in curated SAFE_ALIAS
                break;
            }
        }
        String aliasTarget = SAFE_ALIASES.get(key.toUpperCase(Locale.ROOT));
        if (aliasTarget != null) {
            Optional<CanonicalParameterDefinition> def = registry.findById(aliasTarget);
            if (def.isPresent()) {
                return binding(key, def.get(), SAFE_ALIAS, 1,
                        "Curated SAFE_ALIAS — runtime still uses legacy key " + key);
            }
            return new Binding(key, aliasTarget, 1, SAFE_ALIAS, null, null, null, null, null, false,
                    "Curated alias target missing from registry: " + aliasTarget);
        }
        if (AMBIGUOUS_REASONS.containsKey(key.toUpperCase(Locale.ROOT))) {
            return new Binding(key, null, 0, AMBIGUOUS, null, null, null, null, null, false,
                    AMBIGUOUS_REASONS.get(key.toUpperCase(Locale.ROOT)));
        }
        // Try registry resolve by phrase (aliases) — only accept if unique
        Optional<CanonicalParameterDefinition> byPhrase = registry.resolve(key);
        if (byPhrase.isPresent() && byPhrase.get().liveScorecardParameter() != null) {
            return binding(key, byPhrase.get(), SAFE_ALIAS, 1, "Resolved via registry alias; confirm before activation");
        }
        return new Binding(key, null, 0, NO_MATCH, null, null, null, null, null, false,
                "No safe GACAT liveScorecardParameter / curated alias");
    }

    public static List<Map<String, Object>> inventoryKeys(Iterable<String> legacyKeys) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String k : legacyKeys) {
            out.add(resolve(k).toMap());
        }
        return out;
    }

    private static Binding binding(
            String key, CanonicalParameterDefinition d, String status, int version, String reason) {
        String valueType = d.unit() != null && d.unit().equalsIgnoreCase("PERCENT") ? "PERCENTAGE"
                : (d.unit() != null && (d.unit().equalsIgnoreCase("INR") || d.unit().equalsIgnoreCase("MONEY"))
                ? "MONEY"
                : (d.unit() != null && d.unit().equalsIgnoreCase("SCORE") ? "SCORE"
                : (d.unit() != null && d.unit().equalsIgnoreCase("BOOLEAN") ? "BOOLEAN"
                : (d.unit() != null && d.unit().equalsIgnoreCase("FLAG") ? "BOOLEAN" : "NUMERIC"))));
        boolean ready = d.capability() != null && d.capability().productionReady();
        return new Binding(
                key,
                d.id(),
                version,
                status,
                d.evaluatedFrom(),
                d.businessName(),
                d.unit(),
                valueType,
                d.availability(),
                ready,
                reason);
    }

    /** Apply binding fields onto a scorecard row map (authoring / migration). */
    public static void stampRow(Map<String, Object> row, Binding b) {
        if (row == null || b == null) return;
        row.put("legacyParameterKey", b.legacyParameterKey());
        if (b.canonicalParameterId() != null
                && (EXACT.equals(b.mappingStatus()) || SAFE_ALIAS.equals(b.mappingStatus()))) {
            row.put("canonicalParameterId", b.canonicalParameterId());
            row.put("canonicalDefinitionVersion", b.canonicalDefinitionVersion());
        }
        row.put("mappingStatus", b.mappingStatus());
        if (b.reason() != null) {
            row.put("mappingReason", b.reason());
        }
        if (b.businessName() != null) {
            row.putIfAbsent("factorLabel", b.businessName());
        }
    }

    /**
     * Stamp EXACT/SAFE_ALIAS bindings onto all rows. Leaves AMBIGUOUS/NO_MATCH marked for human justification.
     * Does not change conditions or points.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> stampScorecardJson(Map<String, Object> scorecardJson) {
        Map<String, Object> out = new LinkedHashMap<>(scorecardJson == null ? Map.of() : scorecardJson);
        Object raw = out.get("rows");
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        List<Map<String, Object>> next = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) m);
            String param = row.get("parameter") == null ? null : String.valueOf(row.get("parameter")).trim();
            if (param != null && !param.isBlank()) {
                // Preserve explicit human justification
                if (!Boolean.TRUE.equals(row.get("legacyCustomJustified"))) {
                    stampRow(row, resolve(param));
                } else {
                    row.put("mappingStatus", LEGACY_CUSTOM);
                }
            }
            next.add(row);
        }
        out.put("rows", next);
        return out;
    }
}
