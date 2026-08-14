package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * POLICY-STUDIO-GATE3 — one authoritative executability answer over GACAT
 * {@link CanonicalParameterDefinition.Capability}.
 * <p>
 * Does not create a parallel registry. Distinguishes Policy Test / Runtime / Production.
 */
public final class ParameterExecutabilitySupport {

    public static final String PRODUCTION_READY = "PRODUCTION_READY";
    public static final String RUNTIME_READY_NONPROD = "RUNTIME_READY_NONPROD";
    public static final String POLICY_TEST_READY = "POLICY_TEST_READY";
    public static final String DERIVATION_DEFINED_NOT_IMPLEMENTED = "DERIVATION_DEFINED_NOT_IMPLEMENTED";
    public static final String SOURCE_AVAILABLE_NOT_BOUND = "SOURCE_AVAILABLE_NOT_BOUND";
    public static final String MANUAL_AUTHORISED = "MANUAL_AUTHORISED";
    public static final String DATA_SOURCE_UNAVAILABLE = "DATA_SOURCE_UNAVAILABLE";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    private ParameterExecutabilitySupport() {}

    /** Known PolicyDsl id ↔ snapshot/runtime fact-path aliases (same semantics). */
    public static List<String> runtimeFactAliases(String canonicalParameterId) {
        if (canonicalParameterId == null) return List.of();
        return switch (canonicalParameterId) {
            case "bureau.score" -> List.of("bureau.consumer.score", "compat.BUREAU_SCORE", "BUREAU_SCORE");
            case "bureau.live_unsecured_loan_count" -> List.of(
                    "compat.LIVE_UNSECURED_LOAN_COUNT", "LIVE_UNSECURED_LOAN_COUNT");
            case "bureau.max_dpd_6m" -> List.of("bureau.dpd.max_6m", "compat.MAX_DPD_6M", "MAX_DPD_6M");
            case "bureau.max_dpd_12m" -> List.of("bureau.dpd.max_12m", "compat.MAX_DPD_12M", "MAX_DPD_12M");
            case "bureau.max_dpd_24m" -> List.of("bureau.dpd.max_24m");
            case "bureau.recent_inquiries_90d" -> List.of(
                    "compat.BUREAU_ENQUIRIES_3M", "BUREAU_ENQUIRIES_3M");
            case "bureau.total_monthly_obligation" -> List.of(
                    "MONTHLY_OBLIGATION", "EMI_OBLIGATION", "compat.MONTHLY_OBLIGATION");
            case "bureau.status_ntc" -> List.of("NTC_FLAG", "bureau.thin_file_indicator");
            case "bureau.written_off_account_count" -> List.of("bureau.accounts.written_off_count");
            case "banking.avg_daily_balance_3m" -> List.of("banking.balance.average_3m", "banking.average_balance");
            case "banking.emi_bounce_count_3m" -> List.of("banking.bounce.emi_count_3m");
            case "gst.turnover.trailing_12m" -> List.of("gst.turnover.trailing_12m");
            case "kyc.pan.verified" -> List.of("kyc.pan_verified");
            default -> List.of();
        };
    }

    public static Map<String, Object> evaluate(String parameterId) {
        if (parameterId == null || parameterId.isBlank()) {
            return unavailable(parameterId, "Empty parameter id");
        }
        Optional<CanonicalParameterDefinition> opt = CanonicalParameterRegistry.shared().findById(parameterId.trim());
        if (opt.isEmpty()) {
            // Authoring overlays (Gate-2 write-off helpers) — Policy-Test only unless seeded.
            if (BusinessConceptResolver.WRITEOFF_NON_CC.equals(parameterId)
                    || BusinessConceptResolver.WRITEOFF_CC.equals(parameterId)) {
                return studioOverlay(parameterId,
                        "PolicyBureauMetricService.writeoffCounts",
                        List.of("bureau.tradeline.write_off_amount", "bureau.tradeline.account_status"));
            }
            return unavailable(parameterId, "Not in GACAT / CanonicalParameterRegistry");
        }
        return evaluate(opt.get());
    }

    public static Map<String, Object> evaluate(CanonicalParameterDefinition def) {
        Map<String, Object> out = base(def.id());
        out.put("canonicalParameterId", def.id());
        out.put("businessName", def.businessName());
        out.put("source", def.evaluatedFrom());
        out.put("type", def.type());
        out.put("normalizedFactBinding", def.existingImplementationBinding());
        out.put("calculatorBinding", def.existingImplementationBinding());
        out.put("aliases", def.aliases());
        out.put("runtimeFactAliases", runtimeFactAliases(def.id()));
        out.put("liveRuleParameter", def.liveRuleParameter());
        out.put("liveScorecardParameter", def.liveScorecardParameter());
        out.put("allowCanonicalAuthority", false);

        CanonicalParameterDefinition.Capability cap = def.capability() != null
                ? def.capability()
                : CanonicalParameterDefinition.Capability.defaultsFor(def.type(), def.evaluatedFrom());
        out.put("capability", cap.toMap());
        out.put("missingDataBehaviour", cap.missingDataTreatment() != null
                ? cap.missingDataTreatment()
                : "DATA_INSUFFICIENT");
        out.put("provenanceModel", provenanceModelFor(def, cap));

        List<String> blockers = new ArrayList<>();
        boolean manual = CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type());
        boolean policyTestReady;
        boolean runtimeReady;
        boolean productionReady = cap.productionReady();
        String executionState;

        if (manual) {
            executionState = MANUAL_AUTHORISED;
            policyTestReady = true;
            runtimeReady = true;
            productionReady = true;
            out.put("providerCapabilities", List.of(capRow("MANUAL",
                    "Application / CAM capture — not provider-derived", null)));
        } else if (productionReady && cap.implemented()) {
            executionState = PRODUCTION_READY;
            policyTestReady = true;
            runtimeReady = true;
            out.put("providerCapabilities", List.of(capRow("PRODUCTION", null,
                    def.existingImplementationBinding())));
        } else if (cap.implemented()) {
            // Implemented calculator exists but not certified production.
            // Some of these still run on runtime metric services (e.g. studio bureau helpers).
            boolean knownRuntimeNonProd = isKnownRuntimeNonProd(def.id());
            executionState = knownRuntimeNonProd ? RUNTIME_READY_NONPROD : POLICY_TEST_READY;
            policyTestReady = true;
            runtimeReady = knownRuntimeNonProd;
            productionReady = false;
            blockers.add("Not productionReady — Policy Test / studio path only until certified");
            out.put("providerCapabilities", List.of(capRow("STUDIO_OR_NONPROD", null,
                    def.existingImplementationBinding())));
        } else if (cap.derivationDefined()) {
            executionState = DERIVATION_DEFINED_NOT_IMPLEMENTED;
            policyTestReady = false;
            runtimeReady = false;
            productionReady = false;
            blockers.add("Derivation defined but calculator/binding not implemented");
        } else if (cap.sourceAvailable() && !cap.normalized()) {
            executionState = SOURCE_AVAILABLE_NOT_BOUND;
            policyTestReady = false;
            runtimeReady = false;
            productionReady = false;
            blockers.add("Source available but not normalized/bound to canonical fact");
        } else if (cap.sourceAvailable()) {
            executionState = SOURCE_AVAILABLE_NOT_BOUND;
            policyTestReady = false;
            runtimeReady = false;
            productionReady = false;
            blockers.add("Source flagged available without executable binding");
        } else {
            executionState = DATA_SOURCE_UNAVAILABLE;
            policyTestReady = false;
            runtimeReady = false;
            productionReady = false;
            blockers.add("No usable source currently");
        }

        out.put("executionState", executionState);
        out.put("policyTestReady", policyTestReady);
        out.put("runtimeReady", runtimeReady);
        out.put("productionReady", productionReady);
        out.put("executable", policyTestReady || runtimeReady || productionReady);
        out.put("blockers", blockers);
        return out;
    }

    /** Gate-2 authoring overlays that have Policy Test calculators but no production UW binding. */
    public static Map<String, Object> studioOverlay(String id, String calculator, List<String> ingredients) {
        Map<String, Object> out = base(id);
        out.put("canonicalParameterId", id);
        out.put("source", "Bureau");
        out.put("type", CanonicalParameterDefinition.DERIVED);
        out.put("executionState", POLICY_TEST_READY);
        out.put("policyTestReady", true);
        out.put("runtimeReady", false);
        out.put("productionReady", false);
        out.put("executable", true);
        out.put("calculatorBinding", calculator);
        out.put("normalizedFactBinding", null);
        out.put("rawIngredients", ingredients);
        out.put("authoringOverlay", true);
        out.put("missingDataBehaviour", "DATA_INSUFFICIENT");
        out.put("provenanceModel", "FIXTURE_OR_STUDIO_CALCULATOR");
        out.put("runtimeFactAliases", List.of());
        out.put("blockers", List.of(
                "Studio/authoring overlay — not productionReady; do not treat as Live UW binding"));
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static void stampOnto(Map<String, Object> target, Map<String, Object> exec) {
        if (target == null || exec == null) return;
        target.put("executionState", exec.get("executionState"));
        target.put("policyTestReady", exec.get("policyTestReady"));
        target.put("runtimeReady", exec.get("runtimeReady"));
        target.put("productionReady", exec.get("productionReady"));
        target.put("missingDataBehaviour", exec.get("missingDataBehaviour"));
        target.put("runtimeFactAliases", exec.get("runtimeFactAliases"));
        target.put("executabilityBlockers", exec.get("blockers"));
        target.put("provenanceModel", exec.get("provenanceModel"));
    }

    private static Map<String, Object> capRow(String kind, String note, String binding) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        if (note != null) m.put("note", note);
        if (binding != null) m.put("binding", binding);
        return m;
    }

    private static boolean isKnownRuntimeNonProd(String id) {
        if (id == null) return false;
        // Studio bureau helpers that can run against normalized tradelines in staging/runtime helpers
        // but are explicitly not productionReady in GACAT.
        String x = id.toLowerCase(Locale.ROOT);
        return x.startsWith("bureau.max_dpd_6m")
                || x.startsWith("bureau.cc_overdue")
                || x.startsWith("bureau.overdue.")
                || x.startsWith("bureau.credit_after_overdue")
                || x.startsWith("bureau.inquiries.current_month")
                || x.equals("bureau.status_ntc");
    }

    private static String provenanceModelFor(
            CanonicalParameterDefinition def, CanonicalParameterDefinition.Capability cap) {
        if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type())) {
            return "MANUAL_AUTHORISED";
        }
        if (cap.productionReady()) {
            return "REAL_PROVIDER|DERIVED";
        }
        if (cap.implemented()) {
            return "FIXTURE|SANDBOX_PROVIDER|STUDIO_CALCULATOR";
        }
        return "MISSING_UNTIL_IMPLEMENTED";
    }

    private static Map<String, Object> base(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("canonicalParameterId", id);
        out.put("canonicalDefinitionVersion", "GACAT");
        return out;
    }

    private static Map<String, Object> unavailable(String id, String reason) {
        Map<String, Object> out = base(id);
        out.put("executionState", DATA_SOURCE_UNAVAILABLE);
        out.put("policyTestReady", false);
        out.put("runtimeReady", false);
        out.put("productionReady", false);
        out.put("executable", false);
        out.put("blockers", List.of(reason));
        out.put("allowCanonicalAuthority", false);
        return out;
    }
}
