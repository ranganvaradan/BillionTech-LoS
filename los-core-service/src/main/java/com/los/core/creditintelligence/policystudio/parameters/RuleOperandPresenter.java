package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POLICY-PARAMETER-RESOLVER-1 — independently resolved operands for CM rule cards.
 * Presentation only; technical DSL remains underneath.
 */
public final class RuleOperandPresenter {

    private static final CanonicalParameterRegistry REGISTRY = new CanonicalParameterRegistry();

    private RuleOperandPresenter() {}

    public static CanonicalParameterRegistry registry() {
        return REGISTRY;
    }

    /**
     * Build operand faces for a rule card. EDI/ADB and CLEAN compound are primary walkthroughs;
     * mechanism is generic for future unresolved terms.
     */
    public static List<Map<String, Object>> buildOperands(
            String systemRuleId,
            List<String> dataUsed,
            Map<String, Object> meta,
            Map<String, Object> visualLogic) {

        List<Map<String, Object>> operands = new ArrayList<>();
        String sys = systemRuleId == null ? "" : systemRuleId.toUpperCase(Locale.ROOT);
        Map<String, Object> resolutions = ParameterResolutionSupport.resolutionsOf(meta);

        boolean ediRule = sys.contains("EDI")
                || (dataUsed != null && dataUsed.stream().anyMatch(p ->
                p != null && p.toLowerCase(Locale.ROOT).contains("proposed_edi")));
        boolean adbRule = sys.contains("ADB")
                || (dataUsed != null && dataUsed.stream().anyMatch(p ->
                p != null && p.toLowerCase(Locale.ROOT).contains("avg_daily_balance")));

        if (ediRule && (adbRule || sys.contains("SETTLEMENT") || sys.contains("BANK"))) {
            // Left: ADB or settlements (resolved from registry); Right: Proposed EDI (unresolved until CM)
            if (adbRule || sys.contains("ADB")) {
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
            operands.add(unresolvedOrMapped(
                    "proposed_edi",
                    "Proposed EDI",
                    resolutions,
                    "application.proposed_edi"));
        }

        boolean overdueParent = sys.contains("OVERDUE_EXCEPTION_PARENT") || sys.contains("NO_OVERDUE_EXCEPT");
        if (overdueParent || (visualLogic != null && "EXCEPTION_ALL".equals(String.valueOf(visualLogic.get("kind"))))) {
            // CLEAN child operand — same resolver; do not invent DPD=0
            Map<String, Object> cleanOp = unresolvedOrMapped(
                    "clean_history",
                    "Clean credit history",
                    resolutions,
                    "bureau.credit_after_overdue.clean_history_months");
            // Legacy cleanHistoryDefinition bridge
            if (!ParameterResolutionSupport.isResolved(cleanOp)
                    && meta != null && meta.get(CleanHistoryDefinitionSupport.META_KEY) instanceof Map<?, ?> legacy) {
                @SuppressWarnings("unchecked")
                Map<String, Object> leg = (Map<String, Object>) legacy;
                String st = String.valueOf(leg.getOrDefault("status", CleanHistoryDefinitionSupport.STATUS_UNRESOLVED));
                if (!CleanHistoryDefinitionSupport.STATUS_UNRESOLVED.equals(st)) {
                    cleanOp = fromLegacyClean(leg);
                }
            }
            operands.add(cleanOp);
        }

        return operands;
    }

    private static Map<String, Object> resolvedOrRegistry(
            String key, String label, String registryId, Map<String, Object> resolutions) {
        Map<String, Object> stored = cast(resolutions.get(key));
        if (ParameterResolutionSupport.isResolved(stored)) {
            return faceFromResolution(key, label, stored);
        }
        return REGISTRY.findById(registryId)
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
        // Important: do NOT auto-map EDI/CLEAN to registry entries — CM must resolve
        Map<String, Object> face = unresolvedFace(key, label);
        REGISTRY.findById(registryHintId).ifPresent(hint ->
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
        face.put("status", ParameterResolutionSupport.STATUS_MAPPED);
        face.put("unresolved", false);
        face.put("unavailable", false);
        face.put("resolveAction", false);
        face.put("autoBoundFromRegistry", autoBound);
        face.put("persistence", "READ_MODEL");
        return face;
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
