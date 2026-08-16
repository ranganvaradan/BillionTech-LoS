package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.service.readiness.DataParametersCapabilitySemantics;
import com.los.core.service.readiness.GacatParameterReadinessProjection;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parity guard: Data &amp; Parameters, Policy Studio Gate3, and spine POLICY_TEST must agree
 * on execution capability for every GACAT parameter.
 */
@Service
public class CanonicalParameterCapabilityParityService {

    public static final List<String> GOLDEN_12_DISAGREEMENT_IDS = List.of(
            "bureau.reason_code",
            "bureau.inquiry.purpose",
            "bureau.inquiry.amount",
            "bureau.inquiry.member",
            "bank.transaction.value_date",
            "bank.transaction.counterparty",
            "kyc.pep.screened",
            "kyc.sanctions.cleared",
            "itr.income.total",
            "itr.taxable_income",
            "financial.revenue",
            "financial.pat"
    );

    private final CanonicalParameterExecutionService executionService;

    public CanonicalParameterCapabilityParityService(CanonicalParameterExecutionService executionService) {
        this.executionService = executionService;
        ExecutionCapabilityAuthority.install(executionService);
    }

    public Map<String, Object> runParityCheck() {
        return runParityCheck(PolicyStudioConvergencePresenter.registry());
    }

    public Map<String, Object> runParityCheck(CanonicalParameterRegistry registry) {
        List<CanonicalParameterDefinition> all = registry.all();
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .build();
        EvaluationContext w6 = EvaluationContext.builder().mode(EvaluationMode.W6_ACQUISITION).build();
        EvaluationContext uw = EvaluationContext.builder().mode(EvaluationMode.UNDERWRITING).build();

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> dpVsPsDisagreements = new ArrayList<>();
        List<Map<String, Object>> surfaceVsSpineDisagreements = new ArrayList<>();
        List<Map<String, Object>> golden12 = new ArrayList<>();

        int psClaimsYes = 0;
        int dpClaimsYes = 0;
        int spineCapable = 0;
        int spineW6 = 0;
        int spineUw = 0;

        for (CanonicalParameterDefinition def : all) {
            Map<String, Object> row = evaluateOne(def, ctx, w6, uw);
            rows.add(row);

            boolean ps = Boolean.TRUE.equals(row.get("policyStudioClaimsExecutable"));
            boolean dp = Boolean.TRUE.equals(row.get("dataParametersClaimsExecutable"));
            boolean spine = Boolean.TRUE.equals(row.get("spineHasExecutionCapability"));

            if (ps) psClaimsYes++;
            if (dp) dpClaimsYes++;
            if (spine) spineCapable++;
            if (Boolean.TRUE.equals(row.get("spineWorkflowCapable"))) spineW6++;
            if (Boolean.TRUE.equals(row.get("spineUnderwritingCapable"))) spineUw++;

            if (Boolean.TRUE.equals(row.get("dpVsPsDisagree"))) {
                dpVsPsDisagreements.add(disagreementSlice(row));
            }
            if (Boolean.TRUE.equals(row.get("surfaceVsSpineDisagree"))) {
                surfaceVsSpineDisagreements.add(disagreementSlice(row));
            }
            if (GOLDEN_12_DISAGREEMENT_IDS.contains(def.id())) {
                golden12.add(disagreementSlice(row));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invariant", ParameterTruthAuthorities.INVARIANT);
        out.put("authorities", Map.of(
                "identity", ParameterTruthAuthorities.GACAT_IDENTITY,
                "executionCapability", ParameterTruthAuthorities.EXECUTION_SPINE,
                "productionCertification", ParameterTruthAuthorities.PRODUCTION_CERTIFICATION));
        out.put("comparisonDefinition", Map.of(
                "policyStudio", "ParameterExecutabilitySupport.policyTestReady (spine facade)",
                "dataParameters", "DataParametersCapabilitySemantics.policyTestReady (spine)",
                "truth", "CanonicalParameterExecutionService.hasExecutionCapability(POLICY_TEST)"));
        out.put("catalogueCount", all.size());
        out.put("policyStudioClaimsExecutableCount", psClaimsYes);
        out.put("dataParametersClaimsExecutableCount", dpClaimsYes);
        out.put("spineHasExecutionCapabilityCount", spineCapable);
        out.put("spineWorkflowCapableCount", spineW6);
        out.put("spineUnderwritingCapableCount", spineUw);
        out.put("dataParametersVsPolicyStudioDisagreementCount", dpVsPsDisagreements.size());
        out.put("surfaceVsSpineDisagreementCount", surfaceVsSpineDisagreements.size());
        out.put("dataParametersVsPolicyStudioDisagreements", dpVsPsDisagreements);
        out.put("surfaceVsSpineDisagreements", surfaceVsSpineDisagreements);
        out.put("known12DisagreementsAfter", golden12);
        out.put("parameters", rows);
        out.put("parityPass", dpVsPsDisagreements.isEmpty());
        out.put("spineAligned", surfaceVsSpineDisagreements.isEmpty());
        out.put("note",
                "Execution capability only. Design availability is separate and may differ from executable.");
        return out;
    }

    private Map<String, Object> evaluateOne(
            CanonicalParameterDefinition def,
            EvaluationContext ctx,
            EvaluationContext w6,
            EvaluationContext uw) {
        Map<String, Object> gate3 = ParameterExecutabilitySupport.evaluate(def);
        Map<String, Object> readiness = GacatParameterReadinessProjection.project(def);
        Map<String, Object> dp = DataParametersCapabilitySemantics.project(def, readiness, null);
        Map<String, Object> proj = CanonicalParameterCapabilityProjection.project(def);

        boolean psExecutable = Boolean.TRUE.equals(gate3.get("policyTestReady"));
        boolean dpExecutable = Boolean.TRUE.equals(dp.get("policyTestReady"));
        boolean readinessPt = Boolean.TRUE.equals(readiness.get("policyTestReady"));
        boolean spineCapable = executionService.hasExecutionCapability(def.id(), ctx);
        boolean spineW6Capable = executionService.hasExecutionCapability(def.id(), w6);
        boolean spineUwCapable = executionService.hasExecutionCapability(def.id(), uw);

        List<String> disagreeReasons = new ArrayList<>();
        if (psExecutable != dpExecutable) {
            disagreeReasons.add("POLICY_STUDIO_VS_DATA_PARAMETERS_POLICY_TEST");
        }
        if (psExecutable != readinessPt) {
            disagreeReasons.add("POLICY_STUDIO_VS_DP1_READINESS");
        }

        boolean surfaceVsSpine = psExecutable != spineCapable
                || dpExecutable != spineCapable
                || readinessPt != spineCapable
                || Boolean.TRUE.equals(proj.get("policyTestReady")) != spineCapable;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("canonicalParameterId", def.id());
        row.put("businessName", def.businessName());
        row.put("type", def.type());
        row.put("sourceFamily", def.evaluatedFrom());
        row.put("catalogueImplemented", def.capability() != null && def.capability().implemented());
        row.put("catalogueProductionReady", def.capability() != null && def.capability().productionReady());
        row.put("policyStudioClaimsExecutable", psExecutable);
        row.put("policyStudioExecutionState", gate3.get("executionState"));
        row.put("dataParametersClaimsExecutable", dpExecutable);
        row.put("dataParametersGate3PolicyTestReady", readinessPt);
        row.put("dataParametersPolicyDesignAvailable",
                dp.get("policyDesign") instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("available")));
        row.put("dataParametersSupportStatus",
                dp.get("parameterSupport") instanceof Map<?, ?> m ? m.get("status") : null);
        row.put("spineHasExecutionCapability", spineCapable);
        row.put("spineWorkflowCapable", spineW6Capable);
        row.put("spineUnderwritingCapable", spineUwCapable);
        row.put("productionCertified", false);
        row.put("dpVsPsDisagree", !disagreeReasons.isEmpty());
        row.put("dpVsPsDisagreeReasons", disagreeReasons);
        row.put("surfaceVsSpineDisagree", surfaceVsSpine);
        return row;
    }

    private static Map<String, Object> disagreementSlice(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalParameterId", row.get("canonicalParameterId"));
        m.put("businessName", row.get("businessName"));
        m.put("type", row.get("type"));
        m.put("sourceFamily", row.get("sourceFamily"));
        m.put("policyStudioClaimsExecutable", row.get("policyStudioClaimsExecutable"));
        m.put("policyStudioExecutionState", row.get("policyStudioExecutionState"));
        m.put("dataParametersClaimsExecutable", row.get("dataParametersClaimsExecutable"));
        m.put("dataParametersGate3PolicyTestReady", row.get("dataParametersGate3PolicyTestReady"));
        m.put("spineHasExecutionCapability", row.get("spineHasExecutionCapability"));
        m.put("catalogueImplemented", row.get("catalogueImplemented"));
        m.put("dpVsPsDisagreeReasons", row.get("dpVsPsDisagreeReasons"));
        m.put("surfaceVsSpineDisagree", row.get("surfaceVsSpineDisagree"));
        return m;
    }
}
