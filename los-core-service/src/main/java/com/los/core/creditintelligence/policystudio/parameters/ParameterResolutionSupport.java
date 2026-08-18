package com.los.core.creditintelligence.policystudio.parameters;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * POLICY-PARAMETER-RESOLVER-1 — generic policy-scoped parameter resolution.
 * POLICY_VERSION_DURABLE persistence on rule/document metadata (+ durable overlay store).
 * Does not invent meanings (e.g. CLEAN ≠ DPD=0). Does not grant production authority.
 */
public final class ParameterResolutionSupport {

    public static final String META_KEY = "parameterResolutions";
    public static final String DOC_META_KEY = "policyParameterMappings";

    public static final String STATUS_UNRESOLVED = "UNRESOLVED";
    public static final String STATUS_MAPPED = "MAPPED";
    public static final String STATUS_MANUAL = "MANUAL";
    public static final String STATUS_PROPOSAL_ACCEPTED = "PROPOSAL_ACCEPTED";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    public static final String TYPE_RAW = CanonicalParameterDefinition.RAW;
    public static final String TYPE_DERIVED = CanonicalParameterDefinition.DERIVED;
    public static final String TYPE_MANUAL = CanonicalParameterDefinition.MANUAL;
    public static final String TYPE_UNRESOLVED = "UNRESOLVED";

    public static final String AVAIL_AUTOMATIC = "AVAILABLE_AUTOMATICALLY";
    public static final String AVAIL_DERIVABLE = "DERIVABLE_FROM_AVAILABLE_DATA";
    public static final String AVAIL_MANUAL = "MANUAL_INPUT_AVAILABLE";
    public static final String AVAIL_UNAVAILABLE = "UNAVAILABLE";
    public static final String AVAIL_NEEDS_CONFIG = "NEEDS_CONFIGURATION";
    public static final String AVAIL_NEEDS_INPUT = "NEEDS_INPUT";
    public static final String AVAIL_EXISTING_MAPPING_UNRESOLVED = "EXISTING_MAPPING_UNRESOLVED";
    public static final String STATUS_EXISTING_MAPPING_UNRESOLVED = "EXISTING_MAPPING_UNRESOLVED";

    /** Single Policy Studio mapping-authority states. Not execution readiness. */
    public static final String MAPPING_CURRENT_RESOLVED = "CURRENT_MAPPING_RESOLVED";
    public static final String MAPPING_EXISTING_UNRESOLVED = "EXISTING_MAPPING_UNRESOLVED";
    public static final String MAPPING_NOT_YET_MAPPED = "NOT_YET_MAPPED";

    private ParameterResolutionSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolutionsOf(Map<String, Object> meta) {
        if (meta == null) return Map.of();
        Object raw = meta.get(META_KEY);
        if (raw instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolutionFor(Map<String, Object> meta, String operandKey) {
        if (operandKey == null || operandKey.isBlank()) return null;
        Map<String, Object> all = resolutionsOf(meta);
        Object one = all.get(operandKey);
        return one instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : null;
    }

    public static boolean isResolved(Map<String, Object> resolution) {
        if (resolution == null) return false;
        String s = String.valueOf(resolution.getOrDefault("status", STATUS_UNRESOLVED));
        return STATUS_MAPPED.equals(s) || STATUS_MANUAL.equals(s) || STATUS_PROPOSAL_ACCEPTED.equals(s);
    }

    public static Map<String, Object> mapToExisting(
            CanonicalParameterDefinition def, String originalTerm) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", STATUS_MAPPED);
        out.put("resolutionType", def.type());
        out.put("parameterId", def.id());
        out.put("businessName", def.businessName());
        out.put("evaluatedFrom", def.evaluatedFrom());
        out.put("availability", def.availability());
        out.put("unit", def.unit());
        out.put("period", def.period());
        out.put("howCalculated", def.calculationSummary());
        out.put("originalTerm", originalTerm);
        out.put("persistence", "POLICY_VERSION_DURABLE");
        out.put("resolutionIdentity", PolicyResolutionIdentity.forParameter(def.id()));
        out.put("scope", "POLICY_DRAFT");
        out.put("reusablePromotion", "NOT_IMPLEMENTED");
        out.put("silentlyInvented", false);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static Map<String, Object> manual(
            String businessLabel,
            String dataType,
            String unit,
            String enteredBy,
            String guidance,
            String originalTerm) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", STATUS_MANUAL);
        out.put("resolutionType", TYPE_MANUAL);
        out.put("businessName", businessLabel == null || businessLabel.isBlank()
                ? (originalTerm == null ? "Manual parameter" : originalTerm) : businessLabel);
        out.put("evaluatedFrom", "Manual Input");
        out.put("availability", AVAIL_MANUAL);
        out.put("unit", unit);
        out.put("dataType", dataType == null ? "Money" : dataType);
        out.put("enteredBy", enteredBy == null || enteredBy.isBlank() ? "Credit Analyst" : enteredBy);
        if (guidance != null && !guidance.isBlank()) out.put("guidance", guidance);
        out.put("originalTerm", originalTerm);
        out.put("factSource", true);
        out.put("notManualReviewTreatment", true);
        out.put("persistence", "POLICY_VERSION_DURABLE");
        out.put("resolutionIdentity", PolicyResolutionIdentity.forParameter(
                originalTerm == null ? businessLabel : originalTerm));
        out.put("scope", "POLICY_DRAFT");
        out.put("silentlyInvented", false);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static Map<String, Object> acceptProposal(
            Map<String, Object> proposal, String originalTerm) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", STATUS_PROPOSAL_ACCEPTED);
        out.put("resolutionType", TYPE_DERIVED);
        out.put("businessName", proposal.getOrDefault("businessName", originalTerm));
        out.put("evaluatedFrom", proposal.getOrDefault("evaluatedFrom", "Computed / Derived"));
        out.put("availability", proposal.getOrDefault("resultAvailability", AVAIL_DERIVABLE));
        out.put("howCalculated", proposal.get("proposedCalculation"));
        out.put("proposal", proposal);
        out.put("originalTerm", originalTerm);
        out.put("persistence", "POLICY_VERSION_DURABLE");
        out.put("resolutionIdentity", PolicyResolutionIdentity.forParameter(originalTerm));
        out.put("scope", "POLICY_DRAFT");
        out.put("silentlyInvented", false);
        out.put("confirmedByCreditManager", true);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static Map<String, Object> unavailable(String parameterId, String reason, String originalTerm) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", STATUS_UNAVAILABLE);
        out.put("resolutionType", TYPE_UNRESOLVED);
        out.put("parameterId", parameterId);
        out.put("availability", AVAIL_UNAVAILABLE);
        out.put("message", reason == null
                ? "Parameter is understood but not available from current data sources."
                : reason);
        out.put("originalTerm", originalTerm);
        out.put("persistence", "POLICY_VERSION_DURABLE");
        out.put("resolutionIdentity", PolicyResolutionIdentity.forParameter(
                parameterId == null ? originalTerm : parameterId));
        out.put("distinctFromUnresolved", true);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static String normalizeOperandKey(String term) {
        if (term == null) return "unknown";
        String t = term.trim().toLowerCase(Locale.ROOT);
        if (BusinessConceptMatching.isWriteOffPhrase(t)) {
            return BusinessConceptMatching.isCreditCardExceptionPhrase(t)
                    ? "writeoff_non_cc" : "write_off";
        }
        // GATE2: token-safe — "credit" must not become proposed_edi
        if (BusinessConceptMatching.isProposedEdiPhrase(t)) {
            return "proposed_edi";
        }
        if (t.contains("clean")) {
            return "clean_history";
        }
        if (t.contains("average daily balance") || t.equals("adb")) {
            return "average_daily_balance";
        }
        return t.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    /** Bridge CLEAN resolution into legacy cleanHistoryDefinition for compound visual. */
    public static Map<String, Object> toCleanHistoryBridge(Map<String, Object> resolution) {
        if (resolution == null) return null;
        String status = String.valueOf(resolution.getOrDefault("status", STATUS_UNRESOLVED));
        Map<String, Object> bridge = new LinkedHashMap<>();
        bridge.put("persistence", "POLICY_VERSION_DURABLE");
        bridge.put("silentlyInvented", false);
        bridge.put("viaGenericResolver", true);
        if (STATUS_MANUAL.equals(status)) {
            bridge.put("status", CleanHistoryDefinitionSupport.STATUS_MANUAL_INPUT);
            bridge.put("manualInputLabel", resolution.get("businessName"));
            bridge.put("evaluatedFrom", "Manual Input");
        } else if (STATUS_MAPPED.equals(status) || STATUS_PROPOSAL_ACCEPTED.equals(status)) {
            bridge.put("status", CleanHistoryDefinitionSupport.STATUS_LINKED_EXISTING);
            bridge.put("linkedParameterId", resolution.getOrDefault("parameterId",
                    "bureau.credit_after_overdue.clean_history_months"));
            bridge.put("evaluatedFrom", resolution.getOrDefault("evaluatedFrom", "Bureau"));
            if (STATUS_PROPOSAL_ACCEPTED.equals(status)) {
                bridge.put("status", CleanHistoryDefinitionSupport.STATUS_DRAFT_SESSION);
                bridge.put("notes", resolution.get("howCalculated"));
                bridge.put("confirmedByCreditManager", true);
            }
        } else {
            return null;
        }
        return bridge;
    }
}
