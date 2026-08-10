package com.los.core.creditintelligence.policystudio.parameters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POLICY-CONVERGENCE-1 — CLEAN history CM definition flow.
 * Session/draft representation only — does NOT invent DPD=0 and does NOT migrate DB.
 */
public final class CleanHistoryDefinitionSupport {

    public static final String META_KEY = "cleanHistoryDefinition";
    public static final String STATUS_UNRESOLVED = "UNRESOLVED";
    public static final String STATUS_DRAFT_SESSION = "DRAFT_SESSION";
    public static final String STATUS_MANUAL_INPUT = "MANUAL_INPUT";
    public static final String STATUS_LINKED_EXISTING = "LINKED_EXISTING";

    private CleanHistoryDefinitionSupport() {}

    public static boolean isCleanRelated(String systemRuleId, String blockedReason, List<String> dataUsed) {
        String sys = systemRuleId == null ? "" : systemRuleId.toUpperCase(Locale.ROOT);
        if (sys.contains("OVERDUE_CHILD_3") || sys.contains("CLEAN")
                || sys.contains("OVERDUE_EXCEPTION_PARENT")
                || sys.contains("NO_OVERDUE_EXCEPT")) {
            return true;
        }
        if (blockedReason != null && blockedReason.toLowerCase(Locale.ROOT).contains("clean")) {
            return true;
        }
        return dataUsed != null && dataUsed.stream()
                .anyMatch(p -> p != null && p.contains("clean_history"));
    }

    public static Map<String, Object> unresolvedCardPayload(CanonicalParameterRegistry registry) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("headline", "Clean credit history needs a definition.");
        out.put("status", STATUS_UNRESOLVED);
        out.put("actions", List.of("USE_EXISTING_DEFINITION", "DEFINE", "MANUAL_INPUT"));
        List<CanonicalParameterDefinition> existing = registry.searchCompatibleCleanDefinitions();
        out.put("existingDefinitions", existing.stream()
                .map(CanonicalParameterDefinition::toBusinessView).toList());
        out.put("existingDefinitionAvailable", !existing.isEmpty());
        if (existing.isEmpty()) {
            out.put("existingDefinitionNote",
                    "No reusable clean-history definition is registered yet. Choose Define or Manual input.");
        } else {
            out.put("existingDefinitionNote",
                    "Compatible parameter(s) found — confirm before use. Definitions are not auto-applied.");
        }
        out.put("doNotInvent", true);
        out.put("persistence", "SESSION_DRAFT_ONLY");
        out.put("persistenceNote",
                "Definition is stored on the draft/session metadata only in this phase — "
                        + "no DB migration / tenant vocabulary publish without explicit approval.");
        return out;
    }

    /** Build a session-only business definition (no DSL, no silent DPD=0). */
    public static Map<String, Object> buildDraftDefinition(Map<String, Object> body) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("status", STATUS_DRAFT_SESSION);
        def.put("persistence", "SESSION_DRAFT_ONLY");
        def.put("parameterId", "bureau.credit_after_overdue.clean_history_months");
        def.put("businessName", "Clean history months (post-overdue)");
        def.put("evaluatedFrom", "Bureau");
        // CM-supplied fields — never defaulted to invent meaning
        putIfPresent(def, body, "startEvent");
        putIfPresent(def, body, "maximumPermittedDpd");
        putIfPresent(def, body, "allowOverdueBalance");
        putIfPresent(def, body, "settledOrWriteOffBreaksClean");
        putIfPresent(def, body, "accountsInScope");
        putIfPresent(def, body, "periodWindowMonths");
        putIfPresent(def, body, "notes");
        def.put("confirmedByCreditManager", true);
        def.put("silentlyInvented", false);
        if (!def.containsKey("maximumPermittedDpd") && !def.containsKey("startEvent")) {
            def.put("incomplete", true);
            def.put("message", "Definition saved as draft skeleton — thresholds not invented.");
        }
        return def;
    }

    public static Map<String, Object> linkExisting(String parameterId) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("status", STATUS_LINKED_EXISTING);
        def.put("persistence", "SESSION_DRAFT_ONLY");
        def.put("linkedParameterId", parameterId);
        def.put("evaluatedFrom", "Bureau");
        def.put("silentlyInvented", false);
        def.put("note", "Linked to existing parameter id for this draft only — confirm before activation.");
        return def;
    }

    public static Map<String, Object> manualInput(String label) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("status", STATUS_MANUAL_INPUT);
        def.put("persistence", "SESSION_DRAFT_ONLY");
        def.put("manualInputLabel", label == null || label.isBlank()
                ? "Clean history months (manual)" : label);
        def.put("evaluatedFrom", "Manual Input");
        def.put("silentlyInvented", false);
        return def;
    }

    private static void putIfPresent(Map<String, Object> def, Map<String, Object> body, String key) {
        if (body != null && body.get(key) != null && !String.valueOf(body.get(key)).isBlank()) {
            def.put(key, body.get(key));
        }
    }
}
