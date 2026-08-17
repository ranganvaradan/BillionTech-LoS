package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * POLICY-PARAMETER-RESOLVER-1 — independently resolved operands for CM rule cards.
 * POLICY-SIMPLE-FLOW-INTEGRITY-1 — operands = expression-required params only
 * (no session-wide / substring-"EDI" leakage).
 */
public final class RuleOperandPresenter {

    private RuleOperandPresenter() {}

    public static CanonicalParameterRegistry registry() {
        return CanonicalParameterRegistry.shared();
    }

    /**
     * Build operand faces for a rule card. EDI/ADB and CLEAN compound are primary walkthroughs;
     * mechanism is generic for future unresolved terms.
     * <p>
     * Invariant: a rule receives only operands its executable expression (or explicit compound
     * dependency) requires. Bare {@code String.contains("EDI")} must never be used — it matches
     * {@code MONTHLY_CREDITS}.
     */
    public static List<Map<String, Object>> buildOperands(
            String systemRuleId,
            List<String> dataUsed,
            Map<String, Object> meta,
            Map<String, Object> visualLogic) {

        List<Map<String, Object>> operands = new ArrayList<>();
        Map<String, Object> resolutions = ParameterResolutionSupport.resolutionsOf(meta);

        boolean ediFromExpression = referencesPath(dataUsed, "proposed_edi");
        boolean adbFromExpression = referencesPath(dataUsed, "avg_daily_balance");
        boolean ediRule = ediFromExpression || SystemRuleIdTokens.hasProposedEdiToken(systemRuleId);
        boolean adbRule = adbFromExpression || SystemRuleIdTokens.hasAdbToken(systemRuleId);
        String sys = SystemRuleIdTokens.upper(systemRuleId);

        // Capacity-style cards: ADB/settlements vs Proposed EDI — only with a real EDI token/path
        if (ediRule && (adbRule || sys.contains("SETTLEMENT"))) {
            if (adbRule) {
                operands.add(resolvedOrRegistry(
                        "average_daily_balance",
                        "Average Daily Balance",
                        "banking.avg_daily_balance_3m",
                        resolutions));
            } else if (sys.contains("SETTLEMENT")) {
                operands.add(resolvedOrRegistry(
                        "average_monthly_settlements",
                        "Average monthly settlements",
                        "banking.settlement.count_monthly_avg_3m",
                        resolutions));
            }
            // GACAT seeds application.proposed_edi as MANUAL — bind like ADB (not unresolvedFace)
            operands.add(resolvedOrRegistry(
                    "proposed_edi",
                    "Proposed EDI",
                    "application.proposed_edi",
                    resolutions));
        } else if (ediRule && ediFromExpression && !adbRule && !sys.contains("SETTLEMENT")) {
            // Expression references Proposed EDI without ADB/settlement left — surface EDI only
            operands.add(resolvedOrRegistry(
                    "proposed_edi",
                    "Proposed EDI",
                    "application.proposed_edi",
                    resolutions));
        }

        boolean overdueParent = sys.contains("OVERDUE_EXCEPTION_PARENT") || sys.contains("NO_OVERDUE_EXCEPT");
        if (overdueParent || (visualLogic != null && "EXCEPTION_ALL".equals(String.valueOf(visualLogic.get("kind"))))) {
            // Bind exact GACAT id — vocabulary-gated stub must not appear Execution READY.
            Map<String, Object> cleanOp = resolvedOrRegistry(
                    "clean_history",
                    "Clean history months (post-overdue)",
                    "bureau.credit_after_overdue.clean_history_months",
                    resolutions);
            if (!ParameterResolutionSupport.isResolved(cleanOp)
                    && meta != null && meta.get(CleanHistoryDefinitionSupport.META_KEY) instanceof Map<?, ?> legacy) {
                @SuppressWarnings("unchecked")
                Map<String, Object> leg = (Map<String, Object>) legacy;
                String st = String.valueOf(leg.getOrDefault("status", CleanHistoryDefinitionSupport.STATUS_UNRESOLVED));
                if (!CleanHistoryDefinitionSupport.STATUS_UNRESOLVED.equals(st)) {
                    Map<String, Object> legacyFace = fromLegacyClean(leg);
                    registry().findById("bureau.credit_after_overdue.clean_history_months")
                            .ifPresent(def -> applyGacatHonestyFlags(legacyFace, def));
                    cleanOp = legacyFace;
                }
            }
            operands.add(cleanOp);
        }

        // Expression / metadata canonical parameters (skip constants; skip unrelated session params)
        Set<String> seenKeys = new LinkedHashSet<>();
        Set<String> seenParams = new LinkedHashSet<>();
        for (Map<String, Object> op : operands) {
            if (op.get("operandKey") != null) seenKeys.add(String.valueOf(op.get("operandKey")));
            if (op.get("parameterId") != null) seenParams.add(String.valueOf(op.get("parameterId")));
            if (op.get("suggestedParameterId") != null) {
                seenParams.add(String.valueOf(op.get("suggestedParameterId")));
            }
        }
        for (String path : expressionRequiredPaths(dataUsed, meta)) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.contains("proposed_edi") && !ediRule) {
                continue; // never attach EDI from contaminated hints
            }
            if (seenParams.contains(path)) continue;
            registry().findById(path).ifPresent(def -> {
                String key = operandKeyFor(path);
                if (seenKeys.contains(key)) return;
                seenKeys.add(key);
                seenParams.add(path);
                Map<String, Object> stored = cast(resolutions.get(key));
                if (ParameterResolutionSupport.isResolved(stored)) {
                    operands.add(faceFromResolution(key, def.businessName(), stored));
                } else {
                    operands.add(faceFromDefinition(key, def.businessName(), def, true));
                }
            });
        }

        return operands;
    }

    /** Canonical metric paths a rule expression actually needs (no invented session params). */
    public static List<String> expressionRequiredPaths(List<String> dataUsed, Map<String, Object> meta) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (meta != null && meta.get("parameterId") != null) {
            String pid = String.valueOf(meta.get("parameterId")).trim();
            if (looksLikeCanonicalPath(pid)) paths.add(pid);
        }
        if (meta != null && meta.get("rightParameterId") != null) {
            String pid = String.valueOf(meta.get("rightParameterId")).trim();
            if (looksLikeCanonicalPath(pid)) paths.add(pid);
        }
        if (dataUsed != null) {
            for (String p : dataUsed) {
                if (p != null && looksLikeCanonicalPath(p.trim())) {
                    paths.add(p.trim());
                }
            }
        }
        return new ArrayList<>(paths);
    }

    private static boolean looksLikeCanonicalPath(String p) {
        if (p == null || p.isBlank()) return false;
        if (!p.contains(".")) return false;
        String lower = p.toLowerCase(Locale.ROOT);
        return lower.startsWith("banking.")
                || lower.startsWith("bureau.")
                || lower.startsWith("application.")
                || lower.startsWith("gst.")
                || lower.startsWith("kyc.")
                || lower.startsWith("obligation.");
    }

    private static boolean referencesPath(List<String> dataUsed, String needle) {
        if (dataUsed == null || needle == null) return false;
        String n = needle.toLowerCase(Locale.ROOT);
        return dataUsed.stream().anyMatch(p -> p != null && p.toLowerCase(Locale.ROOT).contains(n));
    }

    private static String operandKeyFor(String path) {
        if (path == null) return "parameter";
        if (path.contains("proposed_edi")) return "proposed_edi";
        if (path.contains("avg_daily_balance")) return "average_daily_balance";
        if (path.contains("monthly_credits")) return "monthly_credits";
        if (path.contains("clean_history")) return "clean_history";
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static Map<String, Object> resolvedOrRegistry(
            String key, String label, String registryId, Map<String, Object> resolutions) {
        Map<String, Object> stored = cast(resolutions.get(key));
        if (ParameterResolutionSupport.isResolved(stored)) {
            Map<String, Object> face = faceFromResolution(key, label, stored);
            // Session resolutions may omit parameterId — still apply GACAT honesty for the bound id
            if (face.get("parameterId") == null && registryId != null) {
                face.put("parameterId", registryId);
            }
            registry().findById(registryId).ifPresent(def -> applyGacatHonestyFlags(face, def));
            com.los.core.creditintelligence.policystudio.parameters.derived
                    .AuthoredDerivedCalculationSupport.overlayOperand(face);
            if (face.get("parameterId") != null) {
                attachCanonicalTruth(face, String.valueOf(face.get("parameterId")));
            } else if (registryId != null) {
                attachCanonicalTruth(face, registryId);
            }
            return face;
        }
        return registry().findById(registryId)
                .map(def -> faceFromDefinition(key, label, def, true))
                .orElseGet(() -> unresolvedFace(key, label));
    }

    private static Map<String, Object> unresolvedOrMapped(
            String key, String label, Map<String, Object> resolutions, String registryHintId) {
        Map<String, Object> stored = cast(resolutions.get(key));
        if (ParameterResolutionSupport.isResolved(stored)) {
            return faceFromResolution(key, label, stored);
        }
        if (stored != null && ParameterResolutionSupport.STATUS_UNAVAILABLE
                .equals(String.valueOf(stored.get("status")))) {
            Map<String, Object> face = faceFromResolution(key, label, stored);
            face.put("message", stored.getOrDefault("message",
                    "Understood, but not available from current data sources."));
            face.put("unresolved", false);
            face.put("unavailable", true);
            face.put("resolveAction", false);
            return face;
        }
        Map<String, Object> face = unresolvedFace(key, label);
        registry().findById(registryHintId).ifPresent(hint ->
                face.put("suggestedParameterId", hint.id()));
        return face;
    }

    private static Map<String, Object> fromLegacyClean(Map<String, Object> leg) {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("operandKey", "clean_history");
        face.put("businessName", "Clean credit history");
        face.put("label", "Clean credit history");
        String st = String.valueOf(leg.get("status"));
        if (CleanHistoryDefinitionSupport.STATUS_MANUAL_INPUT.equals(st)) {
            face.put("resolutionState", ParameterResolutionSupport.TYPE_MANUAL);
            face.put("availability", ParameterResolutionSupport.AVAIL_MANUAL);
            face.put("evaluatedFrom", "Manual Input");
            face.put("status", ParameterResolutionSupport.STATUS_MANUAL);
        } else {
            face.put("resolutionState", ParameterResolutionSupport.TYPE_DERIVED);
            face.put("availability", ParameterResolutionSupport.AVAIL_NEEDS_CONFIG);
            face.put("evaluatedFrom", leg.getOrDefault("evaluatedFrom", "Bureau"));
            face.put("status", ParameterResolutionSupport.STATUS_MAPPED);
            face.put("parameterId", leg.get("linkedParameterId"));
        }
        face.put("unresolved", false);
        face.put("unavailable", false);
        face.put("resolveAction", false);
        face.put("howCalculated", leg.get("notes"));
        face.put("persistence", "SESSION_DRAFT_ONLY");
        Object linked = leg.get("linkedParameterId");
        if (linked != null) {
            registry().findById(String.valueOf(linked)).ifPresent(def -> applyGacatHonestyFlags(face, def));
        } else {
            registry().findById("bureau.credit_after_overdue.clean_history_months")
                    .ifPresent(def -> applyGacatHonestyFlags(face, def));
        }
        return face;
    }

    private static Map<String, Object> faceFromDefinition(
            String key, String label, CanonicalParameterDefinition def, boolean autoBound) {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("operandKey", key);
        face.put("businessName", def.businessName());
        face.put("label", label);
        face.put("parameterId", def.id());
        face.put("resolutionState", def.type());
        face.put("availability", def.availability());
        face.put("availabilityLabel", availabilityCmLabel(def.availability()));
        face.put("evaluatedFrom", def.evaluatedFrom());
        face.put("unit", def.unit());
        face.put("period", def.period());
        face.put("howCalculated", def.calculationSummary());
        boolean manual = CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type());
        if (manual) {
            // Catalogue MANUAL = capture-authorised (same contract as ParameterExecutabilitySupport.MANUAL_AUTHORISED)
            face.put("status", ParameterResolutionSupport.STATUS_MANUAL);
            face.put("availability", ParameterResolutionSupport.AVAIL_MANUAL);
            face.put("availabilityLabel", availabilityCmLabel(ParameterResolutionSupport.AVAIL_MANUAL));
            face.put("evaluatedFrom", "Manual Input");
            face.put("manualInput", true);
            face.put("dataType", def.unit() == null ? "Money" : def.unit());
            face.put("enteredBy", "Application / CAM capture");
        } else {
            face.put("status", ParameterResolutionSupport.STATUS_MAPPED);
        }
        face.put("unresolved", false);
        face.put("unavailable", false);
        face.put("resolveAction", false);
        face.put("autoBoundFromRegistry", autoBound);
        face.put("persistence", "READ_MODEL");
        applyGacatHonestyFlags(face, def);
        // POLICY-STUDIO-RULE-LIFECYCLE-AND-STATE-MODEL-CLOSURE-1 — authored defs clear calc-required
        com.los.core.creditintelligence.policystudio.parameters.derived
                .AuthoredDerivedCalculationSupport.overlayOperand(face);
        attachCanonicalTruth(face, def.id());
        return face;
    }

    /**
     * FINAL-CANONICAL-PARAMETER-STATE — operand faces carry CanonicalParameterState only.
     */
    private static void attachCanonicalTruth(Map<String, Object> face, String canonicalId) {
        if (face == null || canonicalId == null || canonicalId.isBlank()) return;
        CanonicalParameterStateService.stamp(face, canonicalId.trim());
        Map<String, Object> state = CanonicalParameterStateService.state(canonicalId.trim());
        face.put("canonicalTruth", state);
        face.put("canonicalParameterState", state);
    }

    /**
     * Catalogue inclusion ≠ executable calculation. Surface truthful readiness blockers
     * when derivation is vocabulary-gated, not implemented, or not production-capable.
     */
    private static void applyGacatHonestyFlags(Map<String, Object> face, CanonicalParameterDefinition def) {
        if (face == null || def == null) return;
        CanonicalParameterDefinition.Capability cap = def.capability();
        String missing = cap != null ? cap.missingDataTreatment() : null;
        String binding = def.existingImplementationBinding() == null
                ? "" : def.existingImplementationBinding().toLowerCase(Locale.ROOT);
        String summary = def.calculationSummary() == null ? "" : def.calculationSummary().toLowerCase(Locale.ROOT);
        boolean needsConfig = (missing != null && missing.toUpperCase(Locale.ROOT).contains("NEEDS_CONFIGURATION"))
                || binding.contains("vocabulary-gated")
                || summary.contains("must be confirmed")
                || summary.contains("customer-defined");
        boolean spineCapable = ExecutionCapabilityAuthority.hasExecutionCapability(
                def.id(), EvaluationMode.POLICY_TEST);
        boolean calcMissing = cap != null && cap.derivationDefined() && !cap.implemented() && !spineCapable;
        if (calcMissing) {
            face.put("calculationRequired", true);
            face.put("needsConfiguration", true);
            face.put("executionReadinessCause", "CALCULATION_REQUIRED");
            face.put("message", "Calculation is defined in the catalogue but not yet implemented.");
            face.put("availability", ParameterResolutionSupport.AVAIL_NEEDS_CONFIG);
            face.put("availabilityLabel", availabilityCmLabel(ParameterResolutionSupport.AVAIL_NEEDS_CONFIG));
        } else if (needsConfig && !spineCapable) {
            face.put("needsConfiguration", true);
            face.put("executionReadinessCause", "NEEDS_PARAMETER_MAPPING");
            face.put("message", missing != null ? missing
                    : "Parameter requires configuration before it can execute.");
            face.put("availability", ParameterResolutionSupport.AVAIL_NEEDS_CONFIG);
            face.put("availabilityLabel", availabilityCmLabel(ParameterResolutionSupport.AVAIL_NEEDS_CONFIG));
        }
    }

    private static Map<String, Object> faceFromResolution(String key, String label, Map<String, Object> res) {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("operandKey", key);
        face.put("businessName", res.getOrDefault("businessName", label));
        face.put("label", label);
        face.put("parameterId", res.get("parameterId"));
        face.put("resolutionState", res.getOrDefault("resolutionType", ParameterResolutionSupport.TYPE_UNRESOLVED));
        face.put("availability", res.get("availability"));
        face.put("availabilityLabel", availabilityCmLabel(String.valueOf(res.get("availability"))));
        face.put("evaluatedFrom", res.get("evaluatedFrom"));
        face.put("unit", res.get("unit"));
        face.put("period", res.get("period"));
        face.put("howCalculated", res.get("howCalculated"));
        face.put("status", res.get("status"));
        face.put("unresolved", false);
        face.put("unavailable", ParameterResolutionSupport.STATUS_UNAVAILABLE
                .equals(String.valueOf(res.get("status"))));
        face.put("resolveAction", false);
        face.put("persistence", res.getOrDefault("persistence", "SESSION_DRAFT_ONLY"));
        face.put("manualInput", res.get("enteredBy") != null
                || ParameterResolutionSupport.STATUS_MANUAL.equals(String.valueOf(res.get("status"))));
        if (res.get("enteredBy") != null) face.put("enteredBy", res.get("enteredBy"));
        if (res.get("dataType") != null) face.put("dataType", res.get("dataType"));
        if (res.get("factSource") != null) face.put("factSource", res.get("factSource"));
        if (res.get("guidance") != null) face.put("guidance", res.get("guidance"));
        Object pid = res.get("parameterId");
        if (pid == null || String.valueOf(pid).isBlank() || "null".equalsIgnoreCase(String.valueOf(pid))) {
            registry().findByOperandKey(key).ifPresent(def -> face.put("parameterId", def.id()));
        }
        Object boundId = face.get("parameterId");
        if (boundId != null && !String.valueOf(boundId).isBlank() && !"null".equalsIgnoreCase(String.valueOf(boundId))) {
            registry().findById(String.valueOf(boundId)).ifPresent(def -> applyGacatHonestyFlags(face, def));
            com.los.core.creditintelligence.policystudio.parameters.derived
                    .AuthoredDerivedCalculationSupport.overlayOperand(face);
            attachCanonicalTruth(face, String.valueOf(boundId));
        } else {
            com.los.core.creditintelligence.policystudio.parameters.derived
                    .AuthoredDerivedCalculationSupport.overlayOperand(face);
        }
        return face;
    }

    private static Map<String, Object> unresolvedFace(String key, String label) {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("operandKey", key);
        face.put("businessName", label);
        face.put("label", label);
        face.put("resolutionState", ParameterResolutionSupport.TYPE_UNRESOLVED);
        face.put("availability", ParameterResolutionSupport.AVAIL_NEEDS_INPUT);
        face.put("availabilityLabel", "Not yet mapped");
        face.put("evaluatedFrom", "UNKNOWN");
        face.put("status", ParameterResolutionSupport.STATUS_UNRESOLVED);
        face.put("unresolved", true);
        face.put("unavailable", false);
        face.put("resolveAction", true);
        face.put("message", "Not yet mapped");
        face.put("distinctFromUnavailable", true);
        return face;
    }

    private static String availabilityCmLabel(String availability) {
        if (availability == null) return "Unknown";
        return switch (availability) {
            case ParameterResolutionSupport.AVAIL_AUTOMATIC -> "Derived automatically";
            case ParameterResolutionSupport.AVAIL_DERIVABLE -> "Derivable from available data";
            case ParameterResolutionSupport.AVAIL_MANUAL -> "Manual input";
            case ParameterResolutionSupport.AVAIL_UNAVAILABLE -> "Unavailable";
            case ParameterResolutionSupport.AVAIL_NEEDS_CONFIG -> "Needs configuration";
            case ParameterResolutionSupport.AVAIL_NEEDS_INPUT -> "Not yet mapped";
            default -> availability;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return o instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : null;
    }
}
