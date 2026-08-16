package com.los.core.creditintelligence.policystudio.truth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-9 disposition of every readiness/projection helper.
 * No helper may remain unclassified. Wave 10 performs retirement.
 */
public final class ReadinessProjectionDisposition {

    public enum Axis {
        SEMANTIC_METADATA,
        EXECUTION_TRUTH,
        POLICY_STATE,
        CERTIFICATION_STATE,
        ACQUISITION_STATE,
        DISPLAY_ONLY,
        LEGACY_DUPLICATE,
        DANGEROUS_PARALLEL_AUTHORITY
    }

    public enum Disposition {
        KEEP_AS_FACADE,
        REWIRE_TO_CANONICAL_PROJECTION,
        RETIRE_IN_WAVE10
    }

    private ReadinessProjectionDisposition() {}

    public static List<Map<String, Object>> inventory() {
        List<Map<String, Object>> rows = new ArrayList<>();
        row(rows, "CanonicalParameterTruthProjection", Axis.EXECUTION_TRUTH,
                Disposition.KEEP_AS_FACADE, "Wave-9 single truth authority");
        row(rows, "LenderTruthDisplayMapper", Axis.DISPLAY_ONLY,
                Disposition.KEEP_AS_FACADE, "Shared lender copy from truth axes");
        row(rows, "SurfaceCanonicalTruthFacade", Axis.DISPLAY_ONLY,
                Disposition.KEEP_AS_FACADE, "Thin surface overlays");
        row(rows, "GacatSemanticRegistry/Projection", Axis.SEMANTIC_METADATA,
                Disposition.KEEP_AS_FACADE, "Wave-4 semantic authority");
        row(rows, "CanonicalParameterExecutionService/ExecutionResult", Axis.EXECUTION_TRUTH,
                Disposition.KEEP_AS_FACADE, "CPES — consumed, not duplicated");
        row(rows, "ExecutionCapabilityAuthority", Axis.EXECUTION_TRUTH,
                Disposition.KEEP_AS_FACADE, "Spine capability holder");
        row(rows, "CanonicalParameterCapabilityProjection", Axis.EXECUTION_TRUTH,
                Disposition.KEEP_AS_FACADE, "Spine capability view; cert via Wave-8");
        row(rows, "ParameterExecutabilitySupport", Axis.EXECUTION_TRUTH,
                Disposition.KEEP_AS_FACADE, "Gate3 compatibility facade over spine");
        row(rows, "ProductionCertificationService", Axis.CERTIFICATION_STATE,
                Disposition.KEEP_AS_FACADE, "Wave-8 certification ledger");
        row(rows, "GacatParameterReadinessProjection", Axis.LEGACY_DUPLICATE,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION,
                "Source/workflow facts kept; overall ladder demoted under Advanced");
        row(rows, "DataParametersCapabilitySemantics", Axis.LEGACY_DUPLICATE,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION,
                "liveUse/primary overridden by truth in D&P enrich");
        row(rows, "DataParametersAdminService.enrich", Axis.DISPLAY_ONLY,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION, "Attaches canonicalTruth");
        row(rows, "PolicyAuthoringCompleteness", Axis.POLICY_STATE,
                Disposition.KEEP_AS_FACADE, "Rule completeness; operands via spine");
        row(rows, "PolicyExecutionReadiness", Axis.POLICY_STATE,
                Disposition.KEEP_AS_FACADE, "Rule readiness; Gate3 via ParameterExecutabilitySupport");
        row(rows, "PolicyRuleLifecycleProjection", Axis.POLICY_STATE,
                Disposition.KEEP_AS_FACADE, "Rule lifecycle display");
        row(rows, "RuleOperandPresenter", Axis.DISPLAY_ONLY,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION, "Prefer truth primary labels");
        row(rows, "ScorecardConvergenceService.toPickerItem", Axis.DISPLAY_ONLY,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION, "Consumes canonicalTruth");
        row(rows, "WorkflowParameterProvidesCatalog", Axis.ACQUISITION_STATE,
                Disposition.KEEP_AS_FACADE, "Acquisition/provides only — not capability");
        row(rows, "dp1Display.ts OVERALL_READINESS_LABELS", Axis.DISPLAY_ONLY,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION, "Prefer primaryStatusLabel");
        row(rows, "lenderUxCopy.lenderPrimaryStatus", Axis.DANGEROUS_PARALLEL_AUTHORITY,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION,
                "Must prefer backend primaryStatus; local flags are fallback only");
        row(rows, "GacatFactorPicker productionReady badge", Axis.DANGEROUS_PARALLEL_AUTHORITY,
                Disposition.REWIRE_TO_CANONICAL_PROJECTION, "Use certificationLabel / primaryStatus");
        row(rows, "catalogue.implemented as readiness", Axis.DANGEROUS_PARALLEL_AUTHORITY,
                Disposition.RETIRE_IN_WAVE10, "Must not drive readiness");
        row(rows, "catalogue.production_ready as live status", Axis.DANGEROUS_PARALLEL_AUTHORITY,
                Disposition.RETIRE_IN_WAVE10, "Must not drive live / Production Ready");
        row(rows, "definition TESTED as certification", Axis.DANGEROUS_PARALLEL_AUTHORITY,
                Disposition.RETIRE_IN_WAVE10, "TESTED ≠ CERTIFIED");
        row(rows, "policy ACTIVE as live certification", Axis.DANGEROUS_PARALLEL_AUTHORITY,
                Disposition.RETIRE_IN_WAVE10, "ACTIVE ≠ CERTIFIED");
        return rows;
    }

    public static Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wave", 9);
        m.put("unclassifiedAllowed", false);
        m.put("projections", inventory());
        m.put("count", inventory().size());
        return m;
    }

    private static void row(
            List<Map<String, Object>> rows,
            String name,
            Axis axis,
            Disposition disposition,
            String note) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("axis", axis.name());
        r.put("disposition", disposition.name());
        r.put("note", note);
        rows.add(r);
    }
}
