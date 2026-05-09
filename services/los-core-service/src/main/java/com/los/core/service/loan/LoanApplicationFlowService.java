package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.ICreditDecisionService;
import com.los.core.service.assignment.AssignmentRuleApplicationService;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.cam.CreditAppraisalService;
import com.los.core.service.underwriting.ScorecardPolicyEngine;
import com.los.core.service.underwriting.UnderwritingEvaluationService;
import com.los.core.service.underwriting.UnderwritingRuleEngine;
import com.los.core.service.flow.step.FlowStepType;
import com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.vkyc.VkycWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Loan Application Flow Orchestrator — drives the full lifecycle:
 *
 *   DRAFT → submit → KYC_IN_PROGRESS → runKyc → bureauPull →
 *   UNDERWRITING → creditDecision → APPROVED → sanction → SANCTION_ISSUED →
 *   generateKfs → acknowledgeKfs → eSign → ESIGN_PENDING →
 *   (webhook callback) → DISBURSEMENT_PENDING → disburse → DISBURSED (+ LMS handover)
 *
 * Each step validates the current status, executes the business logic,
 * transitions to the next status, and records an audit event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationFlowService {

    private final LoanApplicationRepository applicationRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final WorkflowExecutionCoordinator workflowExecutionCoordinator;
    private final ICreditDecisionService creditDecisionService;
    private final KfsService kfsService;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final AuditService auditService;
    private final UnderwritingRuleEngine underwritingRuleEngine;
    private final ScorecardPolicyEngine scorecardPolicyEngine;
    private final CreditAppraisalService creditAppraisalService;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final CreditControlService creditControlService;
    private final UnderwritingEvaluationService underwritingEvaluationService;
    private final AssignmentRuleApplicationService assignmentRuleApplicationService;
    /**
     * VKYC governance guard — blocks downstream flow steps (CAM review, sanction, eSign,
     * disbursement) until VKYC is auditor-approved when VKYC is configured and applicable
     * for the application. The guard is side-effect free (no DB writes, no audit events).
     */
    private final VkycWorkflowService vkycWorkflowService;

    /**
     * Self-injected proxy reference so that executeFullFlow() calls step methods
     * through the Spring AOP proxy, preserving @Transactional boundaries.
     */
    @Lazy
    @Autowired
    private LoanApplicationFlowService self;

    // ========================== STEP 1: SUBMIT ==========================

    /**
     * Submit a DRAFT application — transitions to KYC_IN_PROGRESS.
     * Validates that required fields are present before submission.
     */
    @Transactional
    public ApplicationResponse submitApplication(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        assertStatus(app, ApplicationStatus.DRAFT, "submit");

        // Validate required fields
        if (app.getRequestedAmount() == null || app.getRequestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Requested amount is required and must be positive");
        }
        if (app.getLoanProduct() == null || app.getLoanProduct().isBlank()) {
            throw new BusinessRuleException("Loan product is required");
        }
        if (app.getPersonalInfo() == null || app.getPersonalInfo().isEmpty()) {
            throw new BusinessRuleException("Personal info is required before submission");
        }

        app.setStatus(ApplicationStatus.KYC_IN_PROGRESS);
        app.setSubmittedAt(Instant.now());
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "SUBMITTED",
                null, Map.of("status", "DRAFT"),
                Map.of("status", "KYC_IN_PROGRESS"),
                "Application submitted — KYC started");

        log.info("Application {} submitted — status: KYC_IN_PROGRESS", app.getApplicationNumber());
        return toResponse(app);
    }

    // ========================== STEP 2: KYC WORKFLOW ==========================

    /**
     * Run the full KYC workflow for the application.
     * Executes all configured KYC steps (PAN, Aadhaar, GSTIN, etc.) via the workflow engine.
     */
    @Transactional
    public Map<String, Object> runKycWorkflow(UUID applicationId, Map<String, Object> kycPayload) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("kycPayload", kycPayload != null ? kycPayload : Map.of());
        return workflowExecutionCoordinator
                .executeFlowStepForApplication(applicationId, FlowStepType.KYC_WORKFLOW, ctx)
                .output();
    }

    /**
     * After a failed KYC run, move the application back to {@code KYC_IN_PROGRESS} so
     * {@link #runKycWorkflow} can be invoked again (same executor path; no change to KYC step logic).
     * Prior {@code KycStepResult} and flow {@code StepExecutionRecord} rows are retained for audit.
     */
    @Transactional
    public ApplicationResponse retryKyc(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.KYC_FAILED) {
            throw new BusinessRuleException(
                    "KYC retry is only allowed when status is KYC_FAILED. Current: " + app.getStatus(),
                    "KYC_RETRY_NOT_ALLOWED",
                    "KYC_RETRY",
                    Map.of("status", app.getStatus().name()));
        }
        app.setStatus(ApplicationStatus.KYC_IN_PROGRESS);
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        // JSONB `newState` / `previousState` use only string values (see SUBMIT) — some PG/Hibernate
        // JSON mappings reject non-String scalars in Map<String,Object> for jsonb columns.
        auditService.logEvent(applicationId, "FLOW", "KYC_RETRY",
                null,
                Map.of("status", "KYC_FAILED"),
                Map.of("status", "KYC_IN_PROGRESS", "retainedHistory", "true"),
                "KYC retry — status reset for re-run; prior attempts retained in history");

        log.info("Application {} KYC retry — status: KYC_FAILED → KYC_IN_PROGRESS", app.getApplicationNumber());
        return toResponse(app);
    }

    // ========================== STEP 3: BUREAU PULL ==========================

    /**
     * Pull credit bureau report (Equifax) for the application.
     * Requires KYC_IN_PROGRESS status. Stores bureau score on the application.
     */
    @Transactional
    public Map<String, Object> pullBureauReport(UUID applicationId) {
        return workflowExecutionCoordinator
                .executeFlowStepForApplication(applicationId, FlowStepType.BUREAU_PULL, Map.of())
                .output();
    }

    // ========================== STEP 4: UNDERWRITING ==========================

    /**
     * Move application to UNDERWRITING and run credit decision engine.
     * Requires KYC_IN_PROGRESS status with at least some successful KYC steps.
     */
    @Transactional
    public Map<String, Object> underwriteApplication(UUID applicationId) {
        return underwriteApplication(applicationId, null);
    }

    @Transactional
    public Map<String, Object> underwriteApplication(UUID applicationId, String evaluatedByUserId) {
        LoanApplication app = findOrThrow(applicationId);
        Map<String, Object> kycOutcome = kycOrchestrationService.computeKycOutcome(applicationId);
        String outcome = String.valueOf(kycOutcome.getOrDefault("outcome", "INCOMPLETE"));
        EffectiveUnderwritingContext ctx = creditControlService.resolveEffective(app, outcome);

        if (!ctx.kycPassEffective()) {
            @SuppressWarnings("unchecked")
            Object stepSummary = kycOutcome.getOrDefault("stepSummary", List.of());
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "UNDERWRITING_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "KYC_OUTCOME_NOT_PASS", "action", "UNDERWRITE", "kycOutcome", outcome, "stepSummary", stepSummary),
                    null,
                    "Underwriting blocked: effective KYC not pass");
            throw new BusinessRuleException(
                    "Underwriting blocked: KYC outcome is not acceptable for the selected KYC source",
                    "KYC_OUTCOME_NOT_PASS",
                    "UNDERWRITE",
                    Map.of("status", app.getStatus().name(), "kycOutcome", outcome, "stepSummary", stepSummary)
            );
        }

        int effBureau = ctx.effectiveBureauScore();
        if (effBureau <= 0) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "UNDERWRITING_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "BUREAU_SCORE_MISSING_OR_NON_POSITIVE", "action", "UNDERWRITE"),
                    null,
                    "Underwriting blocked: effective bureau score missing");
            throw new BusinessRuleException(
                    "Underwriting blocked: effective bureau score is missing for the selected bureau source",
                    "BUREAU_SCORE_MISSING_OR_NON_POSITIVE",
                    "UNDERWRITE",
                    Map.of("status", app.getStatus().name())
            );
        }

        app.setStatus(ApplicationStatus.UNDERWRITING);
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        int creditScore = effBureau;
        java.util.Optional<ScorecardPolicyEngine.ScorecardEvalResult> scOpt = scorecardPolicyEngine.evaluate(
                app, ctx, outcome);
        MultiRuleEvalResult multi;
        java.util.UUID scorecardForRecord = null;
        java.util.List<java.util.Map<String, Object>> paramResults = null;
        if (scOpt.isPresent()) {
            multi = scOpt.get().multi();
            scorecardForRecord = scOpt.get().scorecardId();
            paramResults = scOpt.get().parameterResults();
        } else {
            multi = underwritingRuleEngine.evaluateAll(app, ctx, outcome);
        }
        BigDecimal foirPercent = null;
        if (ctx.effectiveIncome() != null
                && ctx.effectiveIncome().compareTo(BigDecimal.ZERO) > 0
                && ctx.effectiveObligation() != null) {
            foirPercent = ctx.effectiveObligation()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(ctx.effectiveIncome(), 2, java.math.RoundingMode.HALF_UP);
        }
        String reason = multi.aggregateReasons() != null && !multi.aggregateReasons().isEmpty()
                ? multi.aggregateReasons().get(0)
                : "NO_POLICY_REASON";
        log.info(
                "Underwriting Decision -> appId={}, income={}, obligations={}, foirPercent={}, score={}, kycPass={}, demoFallback={}, decision={}, policy={}, reason={}",
                app.getId(),
                ctx.effectiveIncome(),
                ctx.effectiveObligation(),
                foirPercent,
                ctx.effectiveBureauScore(),
                ctx.kycPassEffective(),
                ctx.scorecard() != null && ctx.scorecard().containsKey("DEMO_FALLBACK_ACTIVE"),
                multi.aggregateCreditDecision(),
                multi.aggregatePolicyRecommendation(),
                reason
        );

        ICreditDecisionService.CreditDecisionResult decision = null;
        String underwritingSource;
        UUID matchedRuleId;
        String matchedRuleName;
        String policyRecommendation;

        if (multi.hasAnyRule()) {
            mergeUnderwritingMetaMulti(app, multi, ctx, outcome);
            String aggCredit = multi.aggregateCreditDecision();
            policyRecommendation = multi.aggregatePolicyRecommendation();
            underwritingSource = scOpt.isPresent() ? "SCORECARD" : "RULE";
            if (!multi.perRule().isEmpty()) {
                MultiRuleEvalResult.PerRuleEval first = multi.perRule().get(0);
                matchedRuleId = java.util.UUID.fromString(first.ruleId());
                matchedRuleName = first.ruleName();
            } else {
                matchedRuleId = null;
                matchedRuleName = null;
            }

            if ("MANUAL_REVIEW".equals(aggCredit)) {
                app.setCreditDecision("MANUAL_REVIEW");
                app.setCreditRiskScore(multi.aggregateRiskScore());
                app = applicationRepository.save(app);
                underwritingEvaluationService.record(
                        app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults);
                assignmentRuleApplicationService.applyAfterUnderwriting(app, ctx);
                app = applicationRepository.save(app);
                auditService.logEvent(applicationId, "FLOW", "UNDERWRITING_MANUAL_REVIEW",
                        null, Map.of("status", "UNDERWRITING"),
                        Map.of("aggregate", aggCredit, "ruleCount", multi.perRule().size()),
                        "Policy requires manual review (aggregated rules)");
                return buildUnderwriteResponse(
                        app, "MANUAL_REVIEW", multi.aggregateRiskScore(), creditScore,
                        new ArrayList<>(multi.aggregateReasons()), List.of(), null,
                        underwritingSource, matchedRuleId, matchedRuleName, policyRecommendation,
                        multi);
            }

            if ("REJECTED".equals(aggCredit) || "REJECT".equals(policyRecommendation)) {
                app.setCreditDecision("REJECTED");
                app.setCreditRiskScore(multi.aggregateRiskScore());
                app.setStatus(ApplicationStatus.REJECTED);
                app = applicationRepository.save(app);
                underwritingEvaluationService.record(
                        app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults);
                assignmentRuleApplicationService.applyAfterUnderwriting(app, ctx);
                app = applicationRepository.save(app);
                auditService.logEvent(applicationId, "FLOW", "UNDERWRITING_COMPLETE",
                        null, Map.of("status", "UNDERWRITING"),
                        Map.of("status", app.getStatus().name(), "decision", "REJECTED", "underwritingSource", underwritingSource),
                        "Credit decision: REJECTED (rule aggregate)");
                return buildUnderwriteResponse(
                        app, "REJECTED", multi.aggregateRiskScore(), creditScore,
                        new ArrayList<>(multi.aggregateReasons()), List.of(), null,
                        underwritingSource, matchedRuleId, matchedRuleName, policyRecommendation, multi);
            }

            if ("APPROVED".equals(aggCredit)) {
                app.setCreditDecision("APPROVED");
                app.setCreditRiskScore(multi.aggregateRiskScore());
                app.setStatus(ApplicationStatus.UNDERWRITING_COMPLETED);
                if (app.getInterestRate() != null) {
                    app.setApprovedRate(app.getInterestRate());
                }
                app.setSanctionedAmount(app.getRequestedAmount());
                app = applicationRepository.save(app);
                underwritingEvaluationService.record(
                        app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults);
                assignmentRuleApplicationService.applyAfterUnderwriting(app, ctx);
                app = applicationRepository.save(app);
                creditAppraisalService.ensureCamForApplication(app);
                app.setStatus(ApplicationStatus.CAM_READY);
                app = applicationRepository.save(app);
                auditService.logEvent(applicationId, "FLOW", "UNDERWRITING_COMPLETE",
                        null, Map.of("status", "UNDERWRITING"),
                        Map.of("status", "CAM_READY", "decision", "APPROVED", "underwritingSource", underwritingSource),
                        "Credit approved — CAM generated (CAM_READY)");
                return buildUnderwriteResponse(
                        app, "APPROVED", multi.aggregateRiskScore(), creditScore,
                        new ArrayList<>(), List.of(), null,
                        underwritingSource, matchedRuleId, matchedRuleName, policyRecommendation, multi);
            }

            app.setCreditDecision(aggCredit);
            app.setCreditRiskScore(multi.aggregateRiskScore());
            app = applicationRepository.save(app);
            underwritingEvaluationService.record(
                        app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults);
            assignmentRuleApplicationService.applyAfterUnderwriting(app, ctx);
            app = applicationRepository.save(app);
            return buildUnderwriteResponse(
                    app, aggCredit, multi.aggregateRiskScore(), creditScore,
                    new ArrayList<>(multi.aggregateReasons()), List.of(), null,
                    underwritingSource, matchedRuleId, matchedRuleName, policyRecommendation, multi);
        }

        decision = creditDecisionService.evaluate(applicationId);
        mergeUnderwritingMetaLegacy(app, decision);
        app.setCreditDecision(decision.decision());
        app.setCreditRiskScore(decision.riskScore());
        if ("APPROVED".equals(decision.decision()) || "APPROVED_WITH_CONDITIONS".equals(decision.decision())) {
            app.setStatus(ApplicationStatus.UNDERWRITING_COMPLETED);
            if (decision.recommendedRate() != null) {
                app.setApprovedRate(decision.recommendedRate());
            } else if (app.getInterestRate() != null) {
                app.setApprovedRate(app.getInterestRate());
            }
            app.setSanctionedAmount(app.getRequestedAmount());
            app = applicationRepository.save(app);
            creditAppraisalService.ensureCamForApplication(app);
            app.setStatus(ApplicationStatus.CAM_READY);
        } else {
            app.setStatus(ApplicationStatus.REJECTED);
        }
        app = applicationRepository.save(app);
        MultiRuleEvalResult legacyMulti = new MultiRuleEvalResult(
                List.of(),
                decision.decision(),
                decision.decision(),
                decision.riskScore(),
                decision.reasons() != null ? new ArrayList<>(decision.reasons()) : new ArrayList<>()
        );
        underwritingEvaluationService.record(
                app.getId(), legacyMulti, ctx, evaluatedByUserId, null, null);
        assignmentRuleApplicationService.applyAfterUnderwriting(app, ctx);
        app = applicationRepository.save(app);
        matchedRuleId = null;
        matchedRuleName = null;
        policyRecommendation = decision.decision();
        underwritingSource = "LEGACY";
        auditService.logEvent(applicationId, "FLOW", "UNDERWRITING_COMPLETE",
                null, Map.of("status", "UNDERWRITING"),
                Map.of("status", app.getStatus().name(), "decision", decision.decision(),
                        "riskScore", decision.riskScore(), "underwritingSource", underwritingSource),
                "Credit decision: " + decision.decision());
        log.info("Underwriting for {} — decision: {}, risk score: {}, new status: {}, source: {}",
                app.getApplicationNumber(), decision.decision(), decision.riskScore(), app.getStatus(), underwritingSource);
        return buildUnderwriteResponse(
                app, decision.decision(), decision.riskScore(), decision.creditScore(),
                decision.reasons() != null ? new ArrayList<>(decision.reasons()) : new ArrayList<>(),
                decision.conditions() != null ? new ArrayList<>(decision.conditions()) : new ArrayList<>(),
                decision.recommendedRate(),
                underwritingSource, matchedRuleId, matchedRuleName, policyRecommendation, legacyMulti);
    }

    @SuppressWarnings("unchecked")
    private void mergeUnderwritingMetaMulti(LoanApplication app, MultiRuleEvalResult multi, EffectiveUnderwritingContext ctx, String kycOutcome) {
        Map<String, Object> fi = app.getFinancialInfo() != null ? new HashMap<>(app.getFinancialInfo()) : new HashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        String src = "RULE";
        if (!multi.perRule().isEmpty() && "SCORECARD".equals(multi.perRule().get(0).kind())) {
            src = "SCORECARD";
        }
        meta.put("source", src);
        meta.put("kycOutcomeAtRun", kycOutcome);
        meta.put("recommendation", multi.aggregatePolicyRecommendation());
        meta.put("aggregateCreditDecision", multi.aggregateCreditDecision());
        meta.put("ruleCount", multi.perRule().size());
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (MultiRuleEvalResult.PerRuleEval p : multi.perRule()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("ruleId", p.ruleId());
            s.put("ruleName", p.ruleName());
            s.put("policyDecision", p.policyDecision());
            s.put("creditDecision", p.creditDecision());
            s.put("riskScore", p.riskScore());
            summaries.add(s);
        }
        meta.put("ruleSummaries", summaries);
        meta.put("decisionSources", Map.of(
                "bureauScoreSource", ctx.bureauSource(),
                "incomeSource", ctx.incomeSource(),
                "kycSource", ctx.kycSource()
        ));
        meta.put("reasons", multi.aggregateReasons());
        meta.put("updatedAt", Instant.now().toString());
        fi.put("underwritingMeta", meta);
        app.setFinancialInfo(fi);
    }

    private void mergeUnderwritingMetaLegacy(LoanApplication app, ICreditDecisionService.CreditDecisionResult d) {
        Map<String, Object> fi = app.getFinancialInfo() != null ? new HashMap<>(app.getFinancialInfo()) : new HashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", "LEGACY");
        meta.put("recommendation", d.decision());
        meta.put("reasons", d.reasons() != null ? d.reasons() : List.of());
        meta.put("updatedAt", Instant.now().toString());
        fi.put("underwritingMeta", meta);
        app.setFinancialInfo(fi);
    }

    private Map<String, Object> buildUnderwriteResponse(
            LoanApplication app,
            String decision,
            int riskScore,
            int creditScore,
            List<String> reasons,
            List<String> conditions,
            BigDecimal recommendedRate,
            String underwritingSource,
            UUID ruleSetId,
            String ruleSetName,
            String policyRecommendation,
            MultiRuleEvalResult multi) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", app.getId());
        m.put("applicationNumber", app.getApplicationNumber());
        m.put("status", app.getStatus().name());
        m.put("decision", decision);
        m.put("riskScore", riskScore);
        m.put("creditScore", creditScore);
        m.put("reasons", reasons != null ? reasons : List.of());
        m.put("conditions", conditions != null ? conditions : List.of());
        m.put("recommendedRate", recommendedRate != null ? recommendedRate : "");
        m.put("sanctionedAmount", app.getSanctionedAmount() != null ? app.getSanctionedAmount() : "");
        m.put("underwritingSource", underwritingSource);
        m.put("underwritingRuleSetId", ruleSetId != null ? ruleSetId.toString() : "");
        m.put("underwritingRuleSetName", ruleSetName != null ? ruleSetName : "");
        m.put("policyRecommendation", policyRecommendation != null ? policyRecommendation : "");
        m.put("aggregatePolicyRecommendation", multi != null ? multi.aggregatePolicyRecommendation() : "");
        m.put("aggregateCreditDecision", multi != null ? multi.aggregateCreditDecision() : decision);
        List<Map<String, Object>> ruleResults = new ArrayList<>();
        if (multi != null) {
            for (MultiRuleEvalResult.PerRuleEval p : multi.perRule()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ruleId", p.ruleId());
                row.put("ruleName", p.ruleName());
                row.put("policyDecision", p.policyDecision());
                row.put("creditDecision", p.creditDecision());
                row.put("riskScore", p.riskScore());
                row.put("reasons", p.reasons());
                row.put("kind", p.kind());
                row.put("matchedConditions", p.matchedConditions() != null ? p.matchedConditions() : Map.of());
                row.put("sourceValuesUsed", p.sourceValuesUsed() != null ? p.sourceValuesUsed() : Map.of());
                ruleResults.add(row);
            }
        }
        m.put("underwritingRuleResults", ruleResults);
        return m;
    }

    /**
     * After a rule-based {@code MANUAL_REVIEW} outcome, credit manager approves or rejects.
     */
    @Transactional
    public ApplicationResponse completeManualUnderwritingDecision(UUID applicationId, boolean approve) {
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.UNDERWRITING) {
            throw new BusinessRuleException(
                    "Application must be in UNDERWRITING for manual decision. Current: " + app.getStatus());
        }
        if (app.getCreditDecision() == null || !"MANUAL_REVIEW".equals(app.getCreditDecision())) {
            throw new BusinessRuleException(
                    "No pending manual review — current credit decision: " + app.getCreditDecision());
        }
        if (approve) {
            app.setCreditDecision("APPROVED");
            if (app.getInterestRate() != null) {
                app.setApprovedRate(app.getInterestRate());
            }
            app.setSanctionedAmount(app.getRequestedAmount());
            app = applicationRepository.save(app);
            creditAppraisalService.ensureCamForApplication(app);
            app.setStatus(ApplicationStatus.CAM_READY);
        } else {
            app.setStatus(ApplicationStatus.REJECTED);
            app.setCreditDecision("REJECTED");
        }
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "MANUAL_UW_RESOLVED", null, null,
                Map.of("decision", app.getCreditDecision(), "status", app.getStatus().name()),
                "Manual underwriting " + (approve ? "APPROVE" : "REJECT"));
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse markCamReviewed(UUID applicationId, String approvedByUserId) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.MARK_CAM_REVIEWED);
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.CAM_READY && app.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessRuleException(
                    "CAM review is only allowed from CAM_READY (or legacy APPROVED). Current: " + app.getStatus(),
                    "CAM_REVIEW_INVALID_STATUS",
                    "OPEN_CAM",
                    Map.of("status", app.getStatus().name()));
        }
        java.util.UUID approver = null;
        if (approvedByUserId != null && !approvedByUserId.isBlank()) {
            try {
                approver = java.util.UUID.fromString(approvedByUserId.trim());
            } catch (IllegalArgumentException ignored) {
                // optional header — ignore invalid uuid
            }
        }
        creditAppraisalService.markReviewed(applicationId, approver);
        app.setStatus(ApplicationStatus.CAM_REVIEWED);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "CAM_REVIEWED", null, Map.of("status", app.getStatus().name()),
                Map.of("status", "CAM_REVIEWED"),
                "Credit manager marked CAM as reviewed");
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse markReadyForDisbursement(UUID applicationId) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.READY_FOR_DISBURSEMENT);
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.ESIGN_COMPLETED) {
            throw new BusinessRuleException(
                    "Ready for disbursement requires ESIGN_COMPLETED. Current: " + app.getStatus(),
                    "NOT_ESIGN_COMPLETED",
                    "COMPLETE_ESIGN",
                    Map.of("status", app.getStatus().name()));
        }
        app.setStatus(ApplicationStatus.READY_FOR_DISBURSEMENT);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "READY_FOR_DISBURSEMENT", null,
                Map.of("status", "ESIGN_COMPLETED"),
                Map.of("status", "READY_FOR_DISBURSEMENT"),
                "Application marked ready for disbursement after eSign");
        return toResponse(app);
    }

    /**
     * CAM_REVIEWED → SANCTION_PENDING (formal gate before terms entry).
     */
    @Transactional
    public ApplicationResponse proceedToSanctionPending(UUID applicationId) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.PROCEED_TO_SANCTION_PENDING);
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.CAM_REVIEWED) {
            throw new BusinessRuleException(
                    "Proceed to sanction requires CAM_REVIEWED. Current: " + app.getStatus(),
                    "NOT_CAM_REVIEWED",
                    "MARK_CAM_REVIEWED",
                    Map.of("status", app.getStatus().name()));
        }
        app.setStatus(ApplicationStatus.SANCTION_PENDING);
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "SANCTION_PENDING", null,
                Map.of("status", "CAM_REVIEWED"),
                Map.of("status", "SANCTION_PENDING"),
                "Application moved to sanction pending");
        return toResponse(app);
    }

    /**
     * Reject at CAM / sanction decision stage.
     */
    @Transactional
    public ApplicationResponse rejectAfterCamReview(UUID applicationId, String remarks) {
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.CAM_REVIEWED
                && app.getStatus() != ApplicationStatus.SANCTION_PENDING) {
            throw new BusinessRuleException(
                    "Rejection is allowed from CAM_REVIEWED or SANCTION_PENDING. Current: " + app.getStatus(),
                    "REJECT_STATUS_INVALID",
                    "OPEN_SANCTION",
                    Map.of("status", app.getStatus().name()));
        }
        app.setStatus(ApplicationStatus.REJECTED);
        app.setCreditDecision("REJECTED");
        if (remarks != null && !remarks.isBlank()) {
            app.setRemarks(remarks);
        }
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "POST_CREDIT_REJECT", null, null,
                Map.of("status", "REJECTED", "remarks", remarks != null ? remarks : ""),
                "Post–CAM / sanction stage rejection");
        return toResponse(app);
    }

    // ========================== STEP 5: SANCTION + KFS ==========================

    /**
     * Formal sanction + KFS after CAM is reviewed. Transitions: CAM_REVIEWED (or legacy APPROVED) →
     * SANCTIONED / KFS_GENERATED.
     */
    @Transactional
    public Map<String, Object> sanctionApplication(UUID applicationId, Map<String, Object> sanctionParams) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.SANCTION);
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.CAM_REVIEWED
                && app.getStatus() != ApplicationStatus.SANCTION_PENDING
                && app.getStatus() != ApplicationStatus.APPROVED) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "SANCTION_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "CAM_NOT_REVIEWED", "action", "SANCTION"),
                    null,
                    "Sanction blocked: requires CAM_REVIEWED, SANCTION_PENDING, or legacy APPROVED");
            throw new BusinessRuleException(
                    "Cannot sanction — application must be CAM_REVIEWED, SANCTION_PENDING, or legacy APPROVED. Current: "
                            + app.getStatus(),
                    "STATUS_NOT_CAM_REVIEWED",
                    "SANCTION",
                    Map.of("status", app.getStatus().name())
            );
        }

        if (sanctionParams != null) {
            if (sanctionParams.containsKey("sanctionedAmount")) {
                app.setSanctionedAmount(new BigDecimal(sanctionParams.get("sanctionedAmount").toString()));
            }
            if (sanctionParams.containsKey("interestRate")) {
                app.setApprovedRate(new BigDecimal(sanctionParams.get("interestRate").toString()));
                app.setInterestRate(new BigDecimal(sanctionParams.get("interestRate").toString()));
            }
            if (sanctionParams.containsKey("tenureMonths")) {
                app.setTenureMonths(Integer.parseInt(sanctionParams.get("tenureMonths").toString()));
            }
        }

        if (app.getSanctionedAmount() == null) {
            app.setSanctionedAmount(app.getRequestedAmount());
        }
        if (app.getApprovedRate() == null && app.getInterestRate() != null) {
            app.setApprovedRate(app.getInterestRate());
        }
        if (app.getInterestRate() == null && app.getApprovedRate() != null) {
            app.setInterestRate(app.getApprovedRate());
        }
        if (app.getTenureMonths() == null) {
            throw new BusinessRuleException("Tenure is required for sanction", "TENURE_REQUIRED", "SANCTION", null);
        }

        BigDecimal fee = null;
        if (sanctionParams != null && sanctionParams.get("processingFee") != null) {
            fee = new BigDecimal(sanctionParams.get("processingFee").toString());
        }
        String conditions = sanctionParams != null && sanctionParams.get("conditions") != null
                ? sanctionParams.get("conditions").toString() : null;
        String remarks = sanctionParams != null && sanctionParams.get("remarks") != null
                ? sanctionParams.get("remarks").toString() : null;
        String approvedBy = sanctionParams != null && sanctionParams.get("approvedBy") != null
                ? sanctionParams.get("approvedBy").toString() : null;

        app.setStatus(ApplicationStatus.SANCTIONED);
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        SanctionRecord rec = SanctionRecord.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .approvedAmount(app.getSanctionedAmount())
                .approvedTenure(app.getTenureMonths())
                .interestRate(app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate())
                .processingFee(fee)
                .conditionsText(conditions)
                .remarks(remarks)
                .approvedBy(approvedBy)
                .build();
        sanctionRecordRepository.save(rec);

        Map<String, Object> charges = sanctionParams != null ? sanctionParams : Map.of();
        KfsDocument kfs = kfsService.generateKfs(applicationId, charges);
        app.setStatus(ApplicationStatus.KFS_GENERATED);
        app = applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "SANCTION_KFS",
                null, Map.of("status", "CAM_REVIEWED"),
                Map.of("status", "KFS_GENERATED", "sanctionedAmount", app.getSanctionedAmount().toString(),
                        "kfsId", kfs.getId().toString()),
                "Sanction recorded and KFS generated");

        log.info("Sanction+KFS for {} — amount: {}, rate: {}%, KFS: {}",
                app.getApplicationNumber(), app.getSanctionedAmount(), app.getApprovedRate(), kfs.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", applicationId);
        result.put("applicationNumber", app.getApplicationNumber());
        result.put("status", app.getStatus().name());
        result.put("sanctionedAmount", app.getSanctionedAmount());
        result.put("interestRate", app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate());
        result.put("tenureMonths", app.getTenureMonths());
        result.put("kfsId", kfs.getId());
        result.put("kfsVersion", kfs.getVersion());
        result.put("kfsStatus", kfs.getStatus());
        result.put("coolingOffHours", kfs.getCoolingOffHours());
        return result;
    }

    // ========================== STEP 6: ESIGN ==========================

    /**
     * Initiate eSign on KFS and loan agreement. Transitions to ESIGN_PENDING.
     * The KFS must be acknowledged (and ideally cooling-off period expired) before eSign.
     */
    @Transactional
    public Map<String, Object> initiateESign(UUID applicationId, Map<String, Object> signerInfo) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.INITIATE_ESIGN);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("signerInfo", signerInfo != null ? signerInfo : Map.of());
        return workflowExecutionCoordinator
                .executeFlowStepForApplication(applicationId, FlowStepType.ESIGN, ctx)
                .output();
    }

    /**
     * Complete eSign (called by webhook or manually after eSign provider confirms signing).
     * Transitions from ESIGN_PENDING to DISBURSEMENT_PENDING.
     */
    @Transactional
    public ApplicationResponse completeESign(UUID applicationId, String esignTransactionId) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.COMPLETE_ESIGN);
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.ESIGN_PENDING) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "ESIGN_COMPLETE_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "STATUS_NOT_ESIGN_PENDING", "action", "ESIGN_COMPLETE"),
                    null,
                    "Complete eSign blocked: requires ESIGN_PENDING");
            throw new BusinessRuleException(
                    "Cannot complete eSign — application must be in ESIGN_PENDING status. Current: " + app.getStatus(),
                    "STATUS_NOT_ESIGN_PENDING",
                    "ESIGN_COMPLETE",
                    Map.of("status", app.getStatus().name())
            );
        }

        app.setStatus(ApplicationStatus.ESIGN_COMPLETED);
        if (esignTransactionId != null) {
            app.setEsignTransactionId(esignTransactionId);
        }
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "ESIGN_COMPLETE",
                null, Map.of("status", "ESIGN_PENDING"),
                Map.of("status", "ESIGN_COMPLETED", "esignTransactionId",
                        esignTransactionId != null ? esignTransactionId : ""),
                "eSign completed — ready for disbursement");

        log.info("eSign completed for {} — status: ESIGN_COMPLETED", app.getApplicationNumber());
        return toResponse(app);
    }

    // ========================== STEP 7: DISBURSE + LMS ==========================

    /**
     * Process disbursement and hand over to Encore LMS.
     * Transitions from DISBURSEMENT_PENDING to DISBURSED.
     */
    @Transactional
    public Map<String, Object> disburseAndHandoverToLms(UUID applicationId) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.DISBURSE);
        return workflowExecutionCoordinator
                .executeFlowStepForApplication(applicationId, FlowStepType.DISBURSE, Map.of())
                .output();
    }

    // ========================== FULL FLOW (ALL STEPS) ==========================

    /**
     * Execute the complete flow from submission through disbursement in one call.
     * Useful for automated/batch processing. Each step is logged individually.
     *
     * @param applicationId The application UUID
     * @param kycPayload    KYC data (PAN, Aadhaar number, etc.)
     * @param sanctionParams Optional override for sanction terms
     * @param signerInfo    Signer details for eSign
     * @return Step-by-step results of the entire flow
     */
    public Map<String, Object> executeFullFlow(UUID applicationId,
                                                Map<String, Object> kycPayload,
                                                Map<String, Object> sanctionParams,
                                                Map<String, Object> signerInfo) {
        Map<String, Object> flowResult = new LinkedHashMap<>();
        String currentStep = "SUBMIT";

        try {
            // KYC / bureau / eSign / disburse use WorkflowExecutionCoordinator (optional strict mapping to
            // workflow_configs) and StepExecutionRecordingService; submit, underwriting, sanction, completeESign stay here.
            // Step 1: Submit (via proxy for @Transactional)
            ApplicationResponse submitted = self.submitApplication(applicationId);
            flowResult.put("step1_submit", Map.of("status", submitted.getStatus().name(), "success", true));

            // Step 2: KYC Workflow
            currentStep = "KYC";
            Map<String, Object> kycResult = self.runKycWorkflow(applicationId, kycPayload);
            flowResult.put("step2_kyc", kycResult);

            boolean kycPassed = (boolean) kycResult.getOrDefault("allPassed", false);
            if (!kycPassed) {
                flowResult.put("stoppedAt", "KYC");
                flowResult.put("reason", "KYC workflow had failures");
                return flowResult;
            }

            // Step 3: Bureau Pull
            currentStep = "BUREAU";
            Map<String, Object> bureauResult = self.pullBureauReport(applicationId);
            flowResult.put("step3_bureau", bureauResult);

            // Step 4: Underwriting + Credit Decision
            currentStep = "UNDERWRITING";
            Map<String, Object> underwriteResult = self.underwriteApplication(applicationId);
            flowResult.put("step4_underwriting", underwriteResult);

            String decision = (String) underwriteResult.getOrDefault("decision", "");
            if ("REJECTED".equals(decision)) {
                flowResult.put("stoppedAt", "UNDERWRITING");
                flowResult.put("reason", "Credit decision: REJECTED");
                return flowResult;
            }

            // Step 4b: CAM review (auto for batch / demo)
            currentStep = "CAM_REVIEW";
            LoanApplication appUw = findOrThrow(applicationId);
            if (appUw.getStatus() == ApplicationStatus.CAM_READY) {
                self.markCamReviewed(applicationId, null);
            }

            // Step 5: Sanction + KFS
            currentStep = "SANCTION";
            Map<String, Object> sanctionResult = self.sanctionApplication(applicationId, sanctionParams);
            flowResult.put("step5_sanction", sanctionResult);

            // Step 6: eSign
            currentStep = "ESIGN";
            Map<String, Object> esignResult = self.initiateESign(applicationId, signerInfo);
            flowResult.put("step6_esign", esignResult);

            boolean esignSuccess = (boolean) esignResult.getOrDefault("esignSuccess", false);
            if (esignSuccess) {
                // Auto-complete eSign (in real flow, this comes from webhook callback)
                String txnId = (String) esignResult.getOrDefault("transactionId", "");
                self.completeESign(applicationId, txnId);
                self.markReadyForDisbursement(applicationId);
            } else {
                flowResult.put("stoppedAt", "ESIGN");
                flowResult.put("reason", "eSign initiation failed");
                return flowResult;
            }

            // Step 7: Disburse + LMS
            currentStep = "DISBURSE";
            Map<String, Object> disburseResult = self.disburseAndHandoverToLms(applicationId);
            flowResult.put("step7_disburse", disburseResult);

            flowResult.put("flowComplete", true);
            flowResult.put("finalStatus", "DISBURSED");

        } catch (Exception e) {
            log.error("Full flow failed at step {} for application {}: {}",
                    currentStep, applicationId, e.getMessage());
            flowResult.put("stoppedAt", currentStep);
            flowResult.put("error", e.getMessage());
            flowResult.put("flowComplete", false);
        }

        return flowResult;
    }

    // ========================== HELPERS ==========================

    private LoanApplication findOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private void assertStatus(LoanApplication app, ApplicationStatus expected, String action) {
        if (app.getStatus() != expected) {
            throw new BusinessRuleException(
                    String.format("Cannot %s — application must be in %s status. Current: %s",
                            action, expected, app.getStatus()));
        }
    }

    private ApplicationResponse toResponse(LoanApplication app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .customerId(app.getCustomerId())
                .borrowerType(app.getBorrowerType())
                .loanProduct(app.getLoanProduct())
                .requestedAmount(app.getRequestedAmount())
                .interestRate(app.getInterestRate())
                .tenureMonths(app.getTenureMonths())
                .status(app.getStatus())
                .personalInfo(app.getPersonalInfo())
                .businessInfo(app.getBusinessInfo())
                .financialInfo(app.getFinancialInfo())
                .collateralInfo(app.getCollateralInfo())
                .remarks(app.getRemarks())
                .assignedTo(app.getAssignedTo())
                .sanctionedAmount(app.getSanctionedAmount())
                .approvedRate(app.getApprovedRate())
                .disbursedAmount(app.getDisbursedAmount())
                .disbursedAt(app.getDisbursedAt())
                .lmsReferenceId(app.getLmsReferenceId())
                .esignTransactionId(app.getEsignTransactionId())
                .vkycRequired(app.getVkycRequired())
                .vkycStatus(app.getVkycStatus())
                .vkycCompletedAt(app.getVkycCompletedAt())
                .vkycAgentId(app.getVkycAgentId())
                .vkycAuditorId(app.getVkycAuditorId())
                .vkycReferenceId(app.getVkycReferenceId())
                .vkycUrl(app.getVkycUrl())
                .vkycTransactionId(app.getVkycTransactionId())
                .vkycUrlGeneratedAt(app.getVkycUrlGeneratedAt())
                .vkycUrlExpiryAt(app.getVkycUrlExpiryAt())
                .vkycLastResentAt(app.getVkycLastResentAt())
                .vkycResendCount(app.getVkycResendCount())
                .vkycEmailSent(app.getVkycEmailSent())
                .vkycEmailSentAt(app.getVkycEmailSentAt())
                .vkycGeneratedBy(app.getVkycGeneratedBy())
                .vkycLastEvent(app.getVkycLastEvent())
                .vkycEventPayload(app.getVkycEventPayload())
                .vkycResultPayload(app.getVkycResultPayload())
                .vkycAgentName(app.getVkycAgentName())
                .vkycAgentUpdatedOn(app.getVkycAgentUpdatedOn())
                .vkycCompletedOn(app.getVkycCompletedOn())
                .vkycVideoUrl(app.getVkycVideoUrl())
                .vkycPanImageUrl(app.getVkycPanImageUrl())
                .vkycFaceImageUrl(app.getVkycFaceImageUrl())
                .amlHit(app.getAmlHit())
                .bureauScore(app.getBureauScore())
                .manualBureauScore(app.getManualBureauScore())
                .manualBureauRemarks(app.getManualBureauRemarks())
                .manualBureauDocumentId(app.getManualBureauDocumentId())
                .creditDecision(app.getCreditDecision())
                .creditRiskScore(app.getCreditRiskScore())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .submittedAt(app.getSubmittedAt())
                .build();
    }
}
