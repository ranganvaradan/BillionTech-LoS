package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.security.SingleTenantDeploymentGuard;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.lms.service.LmsApplicationConfigResolver;
import com.los.lms.service.LmsProductMappingResolution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LOS-PRODUCTION-HARDENING-1 — builds immutable evaluation snapshot for historical reproducibility.
 */
@Component
@RequiredArgsConstructor
public class DecisionConfigurationSnapshotBuilder {

    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;
    private final SingleTenantDeploymentGuard singleTenantDeploymentGuard;

    public Map<String, Object> build(
            LoanApplication application,
            MultiRuleEvalResult multi,
            EffectiveUnderwritingContext ctx,
            UUID scorecardId,
            Integer scorecardVersion,
            List<Map<String, Object>> parameterResults,
            Map<String, Object> scorecardEvidence,
            String evaluatedBy) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("snapshotVersion", 1);
        snap.put("immutable", true);
        snap.put("capturedAt", Instant.now().toString());
        snap.put("tenantId", singleTenantDeploymentGuard.deploymentTenantId().toString());
        snap.put("tenancyMode", singleTenantDeploymentGuard.mode());
        snap.put("allowCanonicalAuthority", false);
        snap.put("productionAuthority", "LIVE_UW_PATH");
        snap.put("evaluatedBy", evaluatedBy);

        Map<String, Object> app = new LinkedHashMap<>();
        if (application != null) {
            app.put("applicationId", application.getId() == null ? null : application.getId().toString());
            app.put("applicationNumber", application.getApplicationNumber());
            app.put("borrowerType", application.getBorrowerType() == null ? null : application.getBorrowerType().name());
            app.put("loanProduct", application.getLoanProduct());
            app.put("intakeSegment", application.getIntakeSegment() == null ? null : application.getIntakeSegment().name());
            app.put("requestedAmount", application.getRequestedAmount());
            app.put("tenureMonths", application.getTenureMonths());
        }
        snap.put("application", app);

        Map<String, Object> routing = new LinkedHashMap<>();
        Optional<WorkflowConfig> wf = application == null
                ? Optional.empty()
                : activeWorkflowConfigService.findActiveForApplication(application);
        if (wf.isPresent()) {
            WorkflowConfig w = wf.get();
            routing.put("workflowId", w.getId() == null ? null : w.getId().toString());
            routing.put("workflowVersion", w.getVersion());
            routing.put("workflowName", w.getName());
            routing.put("lmsProductCodeConfigured", w.getLmsProductCode());
        } else if (application != null && application.getWorkflowId() != null) {
            routing.put("workflowId", application.getWorkflowId().toString());
        }
        List<String> ruleSetIds = multi == null || multi.perRule() == null ? List.of()
                : multi.perRule().stream().map(MultiRuleEvalResult.PerRuleEval::ruleId).distinct().collect(Collectors.toList());
        routing.put("ruleSetIds", ruleSetIds);
        routing.put("scorecardId", scorecardId == null ? null : scorecardId.toString());
        routing.put("scorecardVersion", scorecardVersion);
        routing.put("policyStudioReference", null);
        if (application != null) {
            Optional<LmsProductMappingResolution> lms =
                    lmsApplicationConfigResolver.resolveEncoreProductMapping(application);
            if (lms.isPresent()) {
                routing.put("lms", lms.get().toEvidenceMap());
            } else {
                Map<String, Object> missingLms = new LinkedHashMap<>();
                missingLms.put("status", "MISSING");
                missingLms.put("mappingSource", null);
                routing.put("lms", missingLms);
            }
        }
        routing.put("plp", Map.of("status", "NOT_REQUIRED_OR_UNRESOLVED"));
        snap.put("routing", routing);

        List<Map<String, Object>> facts = new ArrayList<>();
        if (parameterResults != null) {
            for (Map<String, Object> p : parameterResults) {
                if (!(p instanceof Map<?, ?>)) continue;
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("parameter", p.get("parameter"));
                f.put("canonicalParameterId", p.get("canonicalParameterId"));
                f.put("canonicalDefinitionVersion", p.get("canonicalDefinitionVersion"));
                f.put("value", p.get("valueUsed"));
                f.put("provenance", p.get("valueProvenance"));
                f.put("authoritativeForDecision", p.get("authoritativeForDecision"));
                facts.add(f);
            }
        }
        snap.put("facts", facts);

        List<Map<String, Object>> rules = new ArrayList<>();
        if (multi != null && multi.perRule() != null) {
            for (MultiRuleEvalResult.PerRuleEval r : multi.perRule()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ruleId", r.ruleId());
                row.put("ruleName", r.ruleName());
                row.put("kind", r.kind());
                row.put("policyDecision", r.policyDecision());
                row.put("creditDecision", r.creditDecision());
                row.put("reasons", r.reasons());
                row.put("matchedConditions", r.matchedConditions());
                rules.add(row);
            }
        }
        snap.put("rules", rules);

        Map<String, Object> scorecard = new LinkedHashMap<>();
        scorecard.put("scorecardId", scorecardId == null ? null : scorecardId.toString());
        scorecard.put("scorecardVersion", scorecardVersion);
        if (scorecardEvidence != null) {
            scorecard.put("evidence", scorecardEvidence);
        }
        scorecard.put("parameterResults", parameterResults == null ? List.of() : parameterResults);
        snap.put("scorecard", scorecard);

        Map<String, Object> decision = new LinkedHashMap<>();
        if (multi != null) {
            decision.put("aggregateCreditDecision", multi.aggregateCreditDecision());
            decision.put("aggregatePolicyRecommendation", multi.aggregatePolicyRecommendation());
            decision.put("aggregateRiskScore", multi.aggregateRiskScore());
            decision.put("aggregateReasons", multi.aggregateReasons());
        }
        if (ctx != null) {
            decision.put("bureauSource", ctx.bureauSource());
            decision.put("incomeSource", ctx.incomeSource());
            decision.put("kycSource", ctx.kycSource());
            decision.put("effectiveBureauScore", ctx.effectiveBureauScore());
            decision.put("kycPassEffective", ctx.kycPassEffective());
        }
        snap.put("decision", decision);
        return snap;
    }
}
