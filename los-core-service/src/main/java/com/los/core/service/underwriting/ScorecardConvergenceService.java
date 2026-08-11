package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.AuthoringValueTypes;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SCORECARD-CONVERGENCE-1 — GACAT-backed factor catalogue, preview, policy suggestions.
 * Reuses {@link ScorecardPolicyEngine}; does not create a new engine.
 */
@Service
@RequiredArgsConstructor
public class ScorecardConvergenceService {

    private final UnderwritingScorecardRepository scorecardRepository;

    public Map<String, Object> factorCatalogue(String q) {
        CanonicalParameterRegistry registry = CanonicalParameterRegistry.shared();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> items = new ArrayList<>();
        for (CanonicalParameterDefinition d : registry.all()) {
            if (!matches(d, query)) continue;
            items.add(toPickerItem(d));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("catalogueAuthority", registry.authority());
        out.put("count", items.size());
        out.put("parameters", items);
        return out;
    }

    public Map<String, Object> mappingInventory() {
        Set<String> keys = new LinkedHashSet<>();
        for (UnderwritingScorecard s : scorecardRepository.findAll()) {
            if (!s.isActive()) continue;
            Object rows = s.getScorecardJson() == null ? null : s.getScorecardJson().get("rows");
            if (rows instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("parameter") != null) {
                        keys.add(String.valueOf(m.get("parameter")).trim());
                    }
                }
            }
            Object hard = s.getHardRulesJson() == null ? null : s.getHardRulesJson().get("rules");
            if (hard instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("parameter") != null) {
                        keys.add(String.valueOf(m.get("parameter")).trim());
                    }
                }
            }
        }
        List<Map<String, Object>> bindings = ScorecardCanonicalFactorMapper.inventoryKeys(keys);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Map<String, Object> b : bindings) {
            String st = String.valueOf(b.get("mappingStatus"));
            byStatus.merge(st, 1L, Long::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("activeScorecardDistinctFactors", keys.size());
        out.put("byMappingStatus", byStatus);
        out.put("bindings", bindings);
        return out;
    }

    /**
     * Preview scorecard evaluation without mutating an application.
     * Body: { scorecardId?, scorecardJson?, thresholdsJson?, hardRulesJson?, safetyJson?, inputs: { KEY: number|string }, provenance?: {} }
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> preview(Map<String, Object> body) {
        UnderwritingScorecard card = resolveCard(body);
        Map<String, BigDecimal> inputs = new LinkedHashMap<>();
        Map<String, String> provenance = new LinkedHashMap<>();
        Object rawIn = body.get("inputs");
        if (rawIn instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                String k = String.valueOf(e.getKey());
                try {
                    inputs.put(k, new BigDecimal(String.valueOf(e.getValue())));
                    provenance.put(k, ScorecardValueProvenance.MANUAL_AUTHORISED);
                } catch (Exception ignored) {
                    // non-numeric left for string path via app personalInfo if needed
                }
            }
        }
        Object rawProv = body.get("provenance");
        if (rawProv instanceof Map<?, ?> pm) {
            for (var e : pm.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    provenance.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }
        int bureau = inputs.getOrDefault("BUREAU_SCORE", BigDecimal.valueOf(700)).intValue();
        LoanApplication app = new LoanApplication();
        app.setStatus(ApplicationStatus.UNDERWRITING);
        app.setBorrowerType(BorrowerType.valueOf(
                card.getBorrowerType() == null ? "COMPANY" : card.getBorrowerType()));
        app.setLoanProduct(card.getLoanProduct() == null ? "TERM_LOAN" : card.getLoanProduct());
        app.setBureauScore(bureau);
        app.setRequestedAmount(card.getMinAmount() != null ? card.getMinAmount() : new BigDecimal("100000"));
        app.setTenureMonths(12);
        app.setPersonalInfo(new LinkedHashMap<>());

        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                bureau,
                true,
                inputs.get("MONTHLY_INCOME"),
                null,
                "MH",
                "Mumbai",
                "BUREAU_PROVIDER",
                "PREVIEW",
                "KYC",
                inputs,
                provenance);

        // Temporarily evaluate via engine by stubbing repository path — call safety scoring directly
        ScorecardSafetyScoring.ScoreOutcome outcome = ScorecardSafetyScoring.score(
                card, app, ctx, true, false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("preview", true);
        out.put("applicationMutated", false);
        out.put("earnedPoints", outcome.earned());
        out.put("maxPoints", outcome.maxPoints());
        out.put("normalizedPercent", outcome.normalizedPercent());
        out.put("policyDecision", outcome.policyDecision());
        out.put("creditDecision", outcome.creditDecision());
        out.put("parameterResults", outcome.parameterResults());
        out.put("evidence", outcome.evidence());
        out.put("rounding", "HALF_UP to integer percent");
        return out;
    }

    /** Suggested factors from an APPROVED policy required-parameter list — CM must Add/Ignore. */
    public Map<String, Object> suggestFromPolicy(List<String> parameterIdsOrKeys) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (parameterIdsOrKeys != null) {
            for (String raw : parameterIdsOrKeys) {
                if (raw == null || raw.isBlank()) continue;
                CanonicalParameterRegistry registry = CanonicalParameterRegistry.shared();
                var def = registry.findById(raw.trim()).or(() -> registry.resolve(raw.trim()));
                if (def.isEmpty()) continue;
                CanonicalParameterDefinition d = def.get();
                Map<String, Object> item = toPickerItem(d);
                item.put("suggested", true);
                item.put("autoCreate", false);
                item.put("actionRequired", "ADD_OR_IGNORE");
                suggestions.add(item);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("note", "Suggestions only — do not auto-create scorecard factors or copy hard-rule thresholds");
        out.put("suggestions", suggestions);
        return out;
    }

    @SuppressWarnings("unchecked")
    private UnderwritingScorecard resolveCard(Map<String, Object> body) {
        if (body.get("scorecardId") != null) {
            UUID id = UUID.fromString(String.valueOf(body.get("scorecardId")));
            return scorecardRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scorecard not found: " + id));
        }
        UnderwritingScorecard card = new UnderwritingScorecard();
        card.setId(UUID.randomUUID());
        card.setName("Preview");
        card.setBorrowerType(String.valueOf(body.getOrDefault("borrowerType", "COMPANY")));
        card.setLoanProduct(String.valueOf(body.getOrDefault("loanProduct", "TERM_LOAN")));
        card.setVersion(1);
        card.setActive(false);
        card.setStatus("DRAFT");
        if (body.get("scorecardJson") instanceof Map<?, ?> m) {
            card.setScorecardJson(new LinkedHashMap<>((Map<String, Object>) m));
        } else {
            card.setScorecardJson(Map.of("rows", List.of()));
        }
        if (body.get("thresholdsJson") instanceof Map<?, ?> m) {
            card.setThresholdsJson(new LinkedHashMap<>((Map<String, Object>) m));
        } else {
            card.setThresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 50));
        }
        if (body.get("hardRulesJson") instanceof Map<?, ?> m) {
            card.setHardRulesJson(new LinkedHashMap<>((Map<String, Object>) m));
        } else {
            card.setHardRulesJson(Map.of("rules", List.of()));
        }
        if (body.get("safetyJson") instanceof Map<?, ?> m) {
            card.setSafetyJson(new LinkedHashMap<>((Map<String, Object>) m));
        } else {
            card.setSafetyJson(Map.of());
        }
        return card;
    }

    private static boolean matches(CanonicalParameterDefinition d, String q) {
        if (q.isEmpty()) return d.liveScorecardParameter() != null
                || (d.capability() != null && d.capability().productionReady());
        String hay = (d.id() + " " + d.businessName() + " " + d.evaluatedFrom() + " "
                + String.join(" ", d.aliases() == null ? List.of() : d.aliases()) + " "
                + (d.liveScorecardParameter() == null ? "" : d.liveScorecardParameter()) + " "
                + (d.liveRuleParameter() == null ? "" : d.liveRuleParameter()))
                .toLowerCase(Locale.ROOT);
        return hay.contains(q);
    }

    private static Map<String, Object> toPickerItem(CanonicalParameterDefinition d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalParameterId", d.id());
        m.put("canonicalDefinitionVersion", 1);
        m.put("businessName", d.businessName());
        m.put("sourceFamily", d.evaluatedFrom());
        m.put("type", d.type());
        m.put("unit", d.unit());
        m.put("availability", d.availability());
        m.put("howObtained", d.calculationSummary() != null ? d.calculationSummary()
                : (d.existingImplementationBinding() != null ? "Via existing LOS path" : null));
        m.put("productionReady", d.capability() != null && d.capability().productionReady());
        m.put("legacyScorecardKey", d.liveScorecardParameter());
        m.put("liveRuleParameter", d.liveRuleParameter());
        m.put("aliases", d.aliases());
        m.put("authoringValueType", AuthoringValueTypes.valueControl(d));
        m.put("allowedValues", AuthoringValueTypes.allowedValues(d.id()));
        // Suggested runtime source for scorecard row
        m.put("suggestedScorecardSource", suggestSource(d));
        Map<String, Object> advanced = new LinkedHashMap<>();
        advanced.put("canonicalId", d.id());
        advanced.put("definitionVersion", 1);
        advanced.put("implementationBinding", d.existingImplementationBinding());
        advanced.put("liveScorecardParameter", d.liveScorecardParameter());
        m.put("advanced", advanced);
        return m;
    }

    private static String suggestSource(CanonicalParameterDefinition d) {
        String from = d.evaluatedFrom() == null ? "" : d.evaluatedFrom().toUpperCase(Locale.ROOT);
        if (from.contains("BUREAU")) return "BUREAU";
        if (from.contains("KYC")) return "SYSTEM";
        if (from.contains("BANK")) return "BANK_STATEMENT";
        if (from.contains("GST")) return "GST";
        if (from.contains("APPLICATION") || from.contains("PRODUCT")) return "APPLICATION";
        if (from.contains("COMPUTED") || from.contains("DERIVED")) return "SYSTEM";
        if (d.liveScorecardParameter() != null) {
            if ("BUREAU_SCORE".equals(d.liveScorecardParameter())) return "BUREAU";
            if ("KYC_QUALITY".equals(d.liveScorecardParameter())) return "SYSTEM";
            if ("AVERAGE_BANK_BALANCE".equals(d.liveScorecardParameter())) return "BANK_STATEMENT";
            if ("OBLIGATION_RATIO".equals(d.liveScorecardParameter())) return "SYSTEM";
        }
        return "SCORECARD";
    }
}
