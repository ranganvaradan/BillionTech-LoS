package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthorableParameterProjection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Projects GACAT authorable parameters as Policy Studio Add Rule capabilities.
 * Same universe as Change Parameter — canonical IDs, not legacy BUREAU.MIN_SCORE templates.
 */
public final class GacatPolicyAuthorableCapabilityFactory {

    public static final String BINDING_KIND = ImplementationBinding.CANONICAL_EVALUATOR;

    private GacatPolicyAuthorableCapabilityFactory() {}

    public static List<BusinessCapability> all(CanonicalParameterRegistry registry) {
        List<BusinessCapability> out = new ArrayList<>();
        for (CanonicalParameterDefinition def : PolicyAuthorableParameterProjection.authorableOf(registry)) {
            toCapability(def).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    public static Optional<BusinessCapability> findAuthorable(
            CanonicalParameterRegistry registry, String canonicalId) {
        return PolicyAuthorableParameterProjection.findAuthorable(registry, canonicalId)
                .flatMap(GacatPolicyAuthorableCapabilityFactory::toCapability);
    }

    public static Optional<BusinessCapability> toCapability(CanonicalParameterDefinition def) {
        if (def == null || def.id() == null || def.id().isBlank()) {
            return Optional.empty();
        }
        if (!PolicyAuthorableParameterProjection.isAuthorable(def)) {
            return Optional.empty();
        }
        boolean boolish = unitLooksBoolean(def);
        BusinessCapability.Builder b = BusinessCapability.builder(def.id())
                .name(def.businessName() == null || def.businessName().isBlank() ? def.id() : def.businessName())
                .description(def.calculationSummary() == null
                        ? "Canonical GACAT parameter — eligibility condition authored by operator + threshold."
                        : def.calculationSummary())
                .domain(domainFor(def.evaluatedFrom(), def.id()))
                .fact(def.id())
                .dataSource(def.evaluatedFrom() == null ? "Needs confirmation" : def.evaluatedFrom())
                .operators(boolish ? new String[]{"EQ"} : new String[]{"LTE", "GTE", "LT", "GT", "EQ"})
                .param(new ParameterDefinition(
                        "operator",
                        "Operator",
                        "ENUM",
                        null,
                        boolish ? "EQ" : defaultOperator(def),
                        "Comparison against the threshold. The condition itself is the eligibility predicate."))
                .param(new ParameterDefinition(
                        "threshold",
                        "Threshold",
                        thresholdType(def, boolish),
                        def.unit(),
                        defaultThreshold(def, boolish),
                        "Value compared with the canonical parameter."))
                .param(new ParameterDefinition(
                        "whenMatched",
                        "When this condition is true",
                        "ENUM",
                        null,
                        "PASS",
                        "PASS = borrower satisfies the eligibility check. FAIL = adverse event — reject/refer."))
                .treatments("REJECT", "MANUAL_REVIEW", "WARNING", "SCORE_IMPACT")
                .parameterisable(true)
                .manualInput(CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type()))
                .manualReview(true)
                .production(false)
                .studio(true)
                .availability("AUTOMATIC")
                .binding(new ImplementationBinding(
                        "gacat-" + def.id(),
                        BINDING_KIND,
                        "CanonicalParameterExecutionService",
                        def.id(),
                        "GACAT authorable projection — not a legacy capability template",
                        false));
        return Optional.of(b.build());
    }

    static CapabilityDomain domainFor(String evaluatedFrom, String id) {
        String src = (evaluatedFrom == null ? "" : evaluatedFrom + " ") + (id == null ? "" : id);
        String folded = src.toLowerCase(Locale.ROOT);
        if (folded.contains("kyc")) return CapabilityDomain.KYC;
        if (folded.contains("gst")) return CapabilityDomain.GST_BUSINESS;
        if (folded.contains("financial") || folded.contains("itr") || folded.contains("dscr")) {
            return CapabilityDomain.FINANCIAL;
        }
        if (folded.contains("bank") || folded.contains("aa.") || folded.contains("account aggregator")) {
            return CapabilityDomain.BANKING;
        }
        if (folded.contains("bureau")) return CapabilityDomain.BUREAU;
        if (folded.contains("application") || folded.contains("customer") || folded.contains("borrower")) {
            return CapabilityDomain.ELIGIBILITY;
        }
        if (folded.contains("collateral")) return CapabilityDomain.COLLATERAL;
        if (folded.contains("manual")) return CapabilityDomain.DECISION_MANUAL_REVIEW;
        return CapabilityDomain.ELIGIBILITY;
    }

    private static boolean unitLooksBoolean(CanonicalParameterDefinition def) {
        String unit = def.unit() == null ? "" : def.unit().toLowerCase(Locale.ROOT);
        String name = def.id() == null ? "" : def.id().toLowerCase(Locale.ROOT);
        return "boolean".equals(unit) || "flag".equals(unit)
                || name.endsWith(".exists") || name.contains("status_ntc")
                || name.endsWith("_ntc");
    }

    private static String defaultOperator(CanonicalParameterDefinition def) {
        String unit = def.unit() == null ? "" : def.unit().toLowerCase(Locale.ROOT);
        if (unit.contains("count") || "count".equals(unit)) {
            return "LTE";
        }
        return "GTE";
    }

    private static String thresholdType(CanonicalParameterDefinition def, boolean boolish) {
        if (boolish) return "ENUM";
        String unit = def.unit() == null ? "" : def.unit().toUpperCase(Locale.ROOT);
        if (unit.contains("MONEY") || "INR".equals(unit) || "AMOUNT".equals(unit)) return "MONEY_INR";
        if (unit.contains("PERCENT") || unit.contains("RATIO")) return "DECIMAL";
        if (unit.contains("SCORE")) return "SCORE";
        return "NUMBER";
    }

    private static Object defaultThreshold(CanonicalParameterDefinition def, boolean boolish) {
        if (boolish) return "true";
        String unit = def.unit() == null ? "" : def.unit().toLowerCase(Locale.ROOT);
        if (unit.contains("score")) return 650;
        if (unit.contains("count")) return 0;
        return 0;
    }
}
