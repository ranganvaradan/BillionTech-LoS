package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures spine capability + execution baselines against Wave 0 golden context.
 */
public final class Wave0SpineBaselineHarness {

    private final CanonicalParameterExecutionService spine;
    private final CanonicalParameterRegistry registry;

    public Wave0SpineBaselineHarness() {
        this(ExecutionSpineProducerBootstrap.standalone(Wave0GoldenDatasets.wave0AuthoredDefinitions()),
                CanonicalParameterRegistry.fromSeedForTestsOnly());
    }

    public Wave0SpineBaselineHarness(
            CanonicalParameterExecutionService spine, CanonicalParameterRegistry registry) {
        this.spine = spine;
        this.registry = registry;
    }

    public CanonicalParameterExecutionService spine() {
        return spine;
    }

    public Map<String, Object> captureCapabilitySnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("wave", "WAVE_1");
        out.put("wave0BaselineCapabilityCounts", Map.of(
                "policyTestCapableCount", 38,
                "w6CapableCount", 28,
                "underwritingCapableCount", 38));
        out.put("wave1CapabilityChangeClass", "EXPECTED_WAVE_1_CONTRACT_CHANGE");
        out.put("wave1CapabilityChangeWhy",
                "BuiltInBankingMetricProducer.hasCapability true without facts (capability != value availability)");
        out.put("gacatAuthority", "GacatCatalogueSeed.all() via CanonicalParameterRegistry.fromSeedForTestsOnly");
        out.put("totalParameters", registry.all().size());

        List<Map<String, Object>> rows = new ArrayList<>();
        int ptCapable = 0;
        int w6Capable = 0;
        int uwCapable = 0;

        EvaluationContext emptyPt = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(Wave0GoldenDatasets.POLICY_TEST_AS_OF)
                .build();
        EvaluationContext emptyW6 = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .evaluationAsOf(Wave0GoldenDatasets.POLICY_TEST_AS_OF)
                .build();
        EvaluationContext emptyUw = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(Wave0GoldenDatasets.POLICY_TEST_AS_OF)
                .build();

        for (CanonicalParameterDefinition def : registry.all()) {
            boolean pt = spine.hasExecutionCapability(def.id(), emptyPt);
            boolean w6 = spine.hasExecutionCapability(def.id(), emptyW6);
            boolean uw = spine.hasExecutionCapability(def.id(), emptyUw);
            if (pt) ptCapable++;
            if (w6) w6Capable++;
            if (uw) uwCapable++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canonicalId", def.id());
            row.put("catalogueType", def.type());
            row.put("policyTestCapable", pt);
            row.put("w6Capable", w6);
            row.put("underwritingCapable", uw);
            row.put("legacyImplemented", def.capability() != null && def.capability().implemented());
            row.put("legacyProductionReady", def.capability() != null && def.capability().productionReady());
            row.put("runtimeFactAliases", ParameterExecutabilitySupport.runtimeFactAliases(def.id()));
            // Producer presence without empty-context value
            ExecutionResult probe = spine.resolveAndExecute(def.id(), emptyPt);
            row.put("emptyContextStatus", probe.status() == null ? null : probe.status().name());
            row.put("producerType", probe.producerType() == null ? null : probe.producerType().name());
            row.put("producerId", probe.producerId());
            rows.add(row);
        }

        out.put("policyTestCapableCount", ptCapable);
        out.put("w6CapableCount", w6Capable);
        out.put("underwritingCapableCount", uwCapable);
        out.put("parameters", rows);
        return out;
    }

    public List<Map<String, Object>> captureExecutionBaseline(EvaluationMode mode) {
        EvaluationContext ctx = Wave0GoldenDatasets.fullGoldenContext(mode);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CanonicalParameterDefinition def : registry.all()) {
            if (!spine.hasExecutionCapability(def.id(), ctx)
                    && !isSpineRegisteredExact(def.id())) {
                // Still capture unsupported Vikasam / interesting IDs
                if (!Wave0GoldenDatasets.VIKASAM_13.contains(def.id())) {
                    continue;
                }
            }
            ExecutionResult r = spine.resolveAndExecute(def.id(), ctx);
            Map<String, Object> row = new LinkedHashMap<>(r.toTraceMap());
            row.put("mode", mode.name());
            row.put("asOf", ctx.evaluationAsOf() == null ? null : ctx.evaluationAsOf().toString());
            row.put("classification", classifyExecution(def.id(), r));
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> captureVikasam13(EvaluationMode mode) {
        EvaluationContext ctx = Wave0GoldenDatasets.fullGoldenContext(mode);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String id : Wave0GoldenDatasets.VIKASAM_13) {
            ExecutionResult r = spine.resolveAndExecute(id, ctx);
            Map<String, Object> row = new LinkedHashMap<>(r.toTraceMap());
            row.put("mode", mode.name());
            row.put("asOf", Wave0GoldenDatasets.POLICY_TEST_AS_OF.toString());
            row.put("policyId", Wave0GoldenDatasets.VIKASAM_POLICY_ID);
            row.put("aliases", ParameterExecutabilitySupport.runtimeFactAliases(id));
            row.put("classification", classifyVikasam(id, r));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> captureW6AcquisitionVsExecutable() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("principle", "SOURCE_ACQUIRED != PARAMETER_AVAILABLE");
        out.put("classification", Wave0Classification.MUST_PRESERVE.name());

        EvaluationContext withFacts = Wave0GoldenDatasets.fullGoldenContext(EvaluationMode.W6_ACQUISITION);
        EvaluationContext withoutPh = Wave0GoldenDatasets.contextWithoutPaymentHistory(EvaluationMode.W6_ACQUISITION);

        List<Map<String, Object>> cases = new ArrayList<>();
        cases.add(w6Case(
                "source_acquired_and_executable",
                "bureau.max_dpd_6m",
                true,
                spine.resolveAndExecute("bureau.max_dpd_6m", withFacts)));
        cases.add(w6Case(
                "source_acquired_parameter_not_executable",
                "bureau.cc_overdue_amount",
                true,
                spine.resolveAndExecute("bureau.cc_overdue_amount", withFacts)));
        cases.add(w6Case(
                "dependency_missing_payment_history",
                "bureau.credit_after_overdue.clean_history_months",
                true,
                spine.resolveAndExecute("bureau.credit_after_overdue.clean_history_months", withoutPh)));
        cases.add(w6Case(
                "source_failure_no_score_fact",
                "bureau.score",
                false,
                spine.resolveAndExecute("bureau.score",
                        EvaluationContext.builder()
                                .mode(EvaluationMode.W6_ACQUISITION)
                                .evaluationAsOf(Wave0GoldenDatasets.POLICY_TEST_AS_OF)
                                .build())));
        out.put("cases", cases);
        return out;
    }

    private static Map<String, Object> w6Case(
            String name, String id, boolean sourceAcquiredClaim, ExecutionResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseId", name);
        m.put("canonicalId", id);
        m.put("sourceAcquiredClaim", sourceAcquiredClaim);
        m.put("spineStatus", r.status() == null ? null : r.status().name());
        m.put("capability", r.capability());
        m.put("valueAvailable", r.valueAvailable());
        m.put("reason", r.reason());
        m.put("note", "Acquisition claim is fixture-level; spine decides availability");
        return m;
    }

    private static boolean isSpineRegisteredExact(String id) {
        return ExecutionSpineProducerBootstrap.RAW_FACT_IDS.contains(id)
                || com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer.EMITTED_IDS.contains(id)
                || "banking.emi_bounce_count_3m".equals(id)
                || "banking.avg_daily_balance_3m".equals(id);
    }

    private static String classifyExecution(String id, ExecutionResult r) {
        if ("bureau.cc_overdue_amount".equals(id)
                || "bureau.overdue.amount".equals(id)
                || "bureau.overdue.age_months".equals(id)) {
            return Wave0Classification.KNOWN_GAP.name() + "|EXPECTED_TO_CHANGE:WAVE_2";
        }
        if ("bureau.dpd_30_plus_count_6m".equals(id) && !r.capability()) {
            return Wave0Classification.KNOWN_GAP.name()
                    + "|unit_baseline_invalid_def|EXPECTED_TO_CHANGE:WAVE_2_OR_CLIENT_DB";
        }
        if (r.status() == ExecutionStatus.VALUE_AVAILABLE && r.capability()) {
            return Wave0Classification.MUST_PRESERVE.name();
        }
        return Wave0Classification.KNOWN_GAP.name();
    }

    private static String classifyVikasam(String id, ExecutionResult r) {
        return switch (id) {
            case "bureau.cc_overdue_amount", "bureau.overdue.amount", "bureau.overdue.age_months" ->
                    Wave0Classification.KNOWN_GAP.name() + "|EXPECTED_TO_CHANGE:WAVE_2";
            case "bureau.dpd_30_plus_count_6m" ->
                    Wave0Classification.KNOWN_GAP.name()
                            + "|Wave0_unit_uses_invalid_MONTHS_SINCE_def|Client_may_differ";
            case "bureau.credit_after_overdue.clean_history_months" ->
                    Wave0Classification.MUST_PRESERVE.name()
                            + "|requires_payment_history_in_context|EXPECTED_TO_CHANGE:WAVE_3_materialization";
            default -> Wave0Classification.MUST_PRESERVE.name();
        };
    }
}
