package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.ProcessOverrideHistory;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.model.enums.VkycStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.ProcessOverrideHistoryRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.ICreditDecisionService;
import com.los.core.service.assignment.AssignmentRuleApplicationService;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.credit.LimitSizingService;
import com.los.core.service.underwriting.AnchorDueDiligenceService;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.cam.CreditAppraisalService;
import com.los.core.service.underwriting.ScorecardPolicyEngine;
import com.los.core.service.underwriting.UnderwritingEvaluationService;
import com.los.core.service.underwriting.UnderwritingRuleEngine;
import com.los.core.creditintelligence.service.CreditIntelligenceFoundationService;
import com.los.core.service.flow.step.FlowStepType;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.core.service.workflow.intake.WorkflowIntakeValidator;
import com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.lms.service.LmsService;
import com.los.plp.service.PlpAnchorSanctionHookService;
import com.los.plp.service.PlpSanctionSyncOrchestrator;
import com.los.plp.support.PlpApplicationSyncFieldMerge;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.enums.ProgramApprovalStatus;
import com.los.core.service.sanction.SanctionApprovedNotifier;
import com.los.core.service.notification.AnchorKfsSignedNotifier;
import com.los.core.service.notification.WelcomeOnboardingNotifier;
import com.los.core.service.esign.EsignSignedApplicationDocumentService;
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
import java.util.regex.Pattern;

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
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final ProcessOverrideHistoryRepository processOverrideHistoryRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final WorkflowExecutionCoordinator workflowExecutionCoordinator;
    private final ICreditDecisionService creditDecisionService;
    private final KfsService kfsService;
    private final LmsService lmsService;
    private final PlpSanctionSyncOrchestrator plpSanctionSyncOrchestrator;
    private final PlpAnchorSanctionHookService plpAnchorSanctionHookService;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final AuditService auditService;
    private final UnderwritingRuleEngine underwritingRuleEngine;
    private final ScorecardPolicyEngine scorecardPolicyEngine;
    private final CreditAppraisalService creditAppraisalService;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final CreditControlService creditControlService;
    private final LimitSizingService limitSizingService;
    private final UnderwritingEvaluationService underwritingEvaluationService;
    private final AssignmentRuleApplicationService assignmentRuleApplicationService;
    private final com.los.core.service.auth.BorrowerAccountProvisioningService borrowerAccountProvisioningService;
    private final com.los.core.service.loan.intake.ApplicationSubmitIdentityValidator applicationSubmitIdentityValidator;
    private final WorkflowIntakeValidator workflowIntakeValidator;
    private final com.los.core.service.integration.itr.ItrWorkflowRequirement itrWorkflowRequirement;
    private final com.los.core.service.integration.gstanalysis.GstAnalysisService gstAnalysisService;
    private final AnchorDueDiligenceService anchorDueDiligenceService;
    private final InvoiceDiscountingSanctionDefaultsService invoiceDiscountingSanctionDefaultsService;
    private final InvoiceDiscountingLosLoanGuard invoiceDiscountingLosLoanGuard;
    private final SanctionApprovedNotifier sanctionApprovedNotifier;
    private final WorkflowRoleGuard workflowRoleGuard;
    private final AnchorKfsSignedNotifier anchorKfsSignedNotifier;
    private final WelcomeOnboardingNotifier welcomeOnboardingNotifier;
    private final EsignSignedApplicationDocumentService esignSignedApplicationDocumentService;
    private final CreditIntelligenceFoundationService creditIntelligenceFoundationService;
    /**
     * VKYC governance guard — blocks downstream flow steps (CAM review, sanction, eSign,
     * disbursement) until VKYC is auditor-approved when VKYC is configured and applicable
     * for the application. The guard is side-effect free (no DB writes, no audit events).
     */
    private final VkycWorkflowService vkycWorkflowService;

    private static final Set<String> DEFAULT_OVERRIDE_ROLES = Set.of(
            "ADMIN",
            "ADMINISTRATOR",
            "RISK_MANAGER",
            "CREDIT_MANAGER",
            "CREDIT_OFFICER"
    );

    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

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
        validateSubmitEmail(app);

        // Enforce the program's Max. dealer limit up-front (before sanction / PLP borrower link) so a
        // requested amount above the program cap is rejected here rather than after LOS sanction.
        invoiceDiscountingSanctionDefaultsService.validateRequestedAmountWithinProgramLimit(app);

        WorkflowConfig workflow = activeWorkflowConfigService.findActiveForApplication(app).orElse(null);
        workflowIntakeValidator.validateAtSubmit(app, workflow);
        itrWorkflowRequirement.requireSuccessIfMandatory(app, "application submission");
        gstAnalysisService.requirePreparedIfMandatory(app, "application submission");
        // Karza GST docs upload / report generation is admin-driven on application review (not on submit).

        // Borrower portal access: ensure the applicant has a LosUser (auto-provision if new) and link the
        // application to it, so admin/staff-created applications are visible to the borrower. Anchor-segment
        // applications are skipped (their downstream borrowers are handled via the PLP flow).
        provisionAndLinkBorrowerIfApplicable(app);

        // Reject re-used email / PAN / GSTN that belong to a different borrower.
        applicationSubmitIdentityValidator.validateNoDuplicateIdentity(app);

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

    /**
     * Mandatory email + format check applied at the final submit step (DRAFT → KYC_IN_PROGRESS)
     * for INDIVIDUAL borrowers. Email is intentionally not required at CREATE for individuals
     * because the borrower email field is collected on the "Basic Borrower Details" step, which
     * can render after the application is first persisted (e.g. Invoice Discounting + BORROWER
     * onboarding creates the DRAFT at the Product & Request step so the next step can link a PLP
     * program). The anchor-segment / business-borrower paths are unchanged: their existing
     * create-time email check ({@code validateCreateEmailForAnchor}) keeps enforcing email there.
     */
    private static void validateSubmitEmail(LoanApplication app) {
        if (app.getBorrowerType() != BorrowerType.INDIVIDUAL
                || app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            return;
        }
        Map<String, Object> personalInfo = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        String email = String.valueOf(personalInfo.getOrDefault("email", "")).trim();
        if (email.isBlank()) {
            throw new BusinessRuleException(
                    "Email is required",
                    "EMAIL_REQUIRED",
                    "SUBMIT_APPLICATION",
                    Map.of("field", "personalInfo.email"));
        }
        if (!EMAIL_RE.matcher(email).matches()) {
            throw new BusinessRuleException(
                    "Please enter a valid email address",
                    "EMAIL_INVALID",
                    "SUBMIT_APPLICATION",
                    Map.of("field", "personalInfo.email"));
        }
    }

    /**
     * Finds or creates the borrower {@link com.los.core.model.entity.LosUser} for a non-anchor application
     * from its email/mobile/name and links the application to it (so it appears in the borrower portal).
     * No-op for anchor-segment applications and when no email is present. Never overwrites an existing
     * user's credentials; newly created borrowers must reset their temporary password on first login.
     */
    private void provisionAndLinkBorrowerIfApplicable(LoanApplication app) {
        if (app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            return;
        }
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            return;
        }
        String mobile = ApplicationPartyResolver.resolveMobile(app);
        String name = ApplicationPartyResolver.resolveDisplayName(app);
        borrowerAccountProvisioningService.findOrCreateBorrower(name, email, mobile)
                .ifPresent(user -> {
                    app.setCustomerId(user.getId());
                    Map<String, Object> pi = app.getPersonalInfo() != null
                            ? new HashMap<>(app.getPersonalInfo()) : new HashMap<>();
                    pi.put("borrowerUserId", user.getId().toString());
                    app.setPersonalInfo(pi);
                });
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
        LoanApplication app = findOrThrow(applicationId);
        if (InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            throw new BusinessRuleException(
                    "Credit bureau pull is not used for invoice discounting anchor onboarding — use due diligence credit rating",
                    "BUREAU_NOT_APPLICABLE",
                    "ANCHOR_DUE_DILIGENCE",
                    null);
        }
        return workflowExecutionCoordinator
                .executeFlowStepForApplication(applicationId, FlowStepType.BUREAU_PULL, Map.of())
                .output();
    }

    // ========================== ANCHOR DUE DILIGENCE (INVOICE DISCOUNTING) ==========================

    public Map<String, Object> getAnchorDueDiligence(UUID applicationId) {
        return anchorDueDiligenceService.getDueDiligence(applicationId);
    }

    @Transactional
    public Map<String, Object> saveAnchorDueDiligence(UUID applicationId, Map<String, Object> answers) {
        return anchorDueDiligenceService.saveDueDiligence(applicationId, answers);
    }

    @Transactional
    public Map<String, Object> completeAnchorDueDiligenceUnderwriting(UUID applicationId) {
        return anchorDueDiligenceService.completeDueDiligenceUnderwriting(applicationId);
    }

    @Transactional
    public ApplicationResponse resolveAnchorManualUnderwriting(UUID applicationId, boolean approve) {
        LoanApplication app = anchorDueDiligenceService.resolveManualReview(applicationId, approve);
        return toResponse(app);
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
        if (InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            throw new BusinessRuleException(
                    "Standard underwriting does not apply to anchor onboarding — complete the due diligence checklist and credit rating",
                    "UNDERWRITING_NOT_APPLICABLE",
                    "ANCHOR_DUE_DILIGENCE",
                    null);
        }
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

        // When GST_ANALYSIS is on the active workflow, require report SUCCESS (or override) before underwriting.
        if (gstAnalysisService.isOnWorkflow(applicationId)
                && !gstAnalysisService.isReportCompleteForUnderwriting(applicationId)) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "UNDERWRITING_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "GST_ANALYSIS_INCOMPLETE", "action", "UNDERWRITE"),
                    null,
                    "Underwriting blocked: GST analysis KYC step incomplete");
            throw new BusinessRuleException(
                    "Underwriting blocked: GST analysis is configured on the workflow and must complete (report generated) before underwriting",
                    "GST_ANALYSIS_INCOMPLETE",
                    "UNDERWRITE",
                    Map.of("status", app.getStatus().name())
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

        java.util.Optional<CreditIntelligenceFoundationService.PrepResult> ciPrep = java.util.Optional.empty();
        try {
            ciPrep = creditIntelligenceFoundationService.prepare(app, ctx, outcome, evaluatedByUserId);
        } catch (Exception ciEx) {
            log.warn("Credit intelligence prepare ignored for {}: {}", applicationId, ciEx.getMessage());
        }

        int creditScore = effBureau;
        MultiRuleEvalResult ruleMulti = underwritingRuleEngine.evaluateAll(app, ctx, outcome);
        boolean hardRuleTriggered = ruleMulti.perRule() != null
                && ruleMulti.perRule().stream().anyMatch(p -> "HARD_RULE".equalsIgnoreCase(p.kind()));
        // Always evaluate scorecard when matched so UI can show the Application credit score panel.
        // When a hard rule already decided, keep the hard-rule decision and only record scorecard params.
        java.util.Optional<ScorecardPolicyEngine.ScorecardEvalResult> scOpt =
                scorecardPolicyEngine.evaluate(app, ctx, outcome);
        MultiRuleEvalResult multi;
        java.util.UUID scorecardForRecord = null;
        java.util.List<java.util.Map<String, Object>> paramResults = null;
        if (hardRuleTriggered && ruleMulti.hasAnyRule()) {
            multi = ruleMulti;
            if (scOpt.isPresent()) {
                scorecardForRecord = scOpt.get().scorecardId();
                paramResults = scOpt.get().parameterResults();
            }
        } else if (scOpt.isPresent()) {
            multi = scOpt.get().multi();
            scorecardForRecord = scOpt.get().scorecardId();
            paramResults = scOpt.get().parameterResults();
        } else {
            multi = ruleMulti;
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
            underwritingSource = (!hardRuleTriggered && scOpt.isPresent()) ? "SCORECARD" : "RULE";
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
                var prodEval = underwritingEvaluationService.record(app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults, app);
                creditIntelligenceFoundationService.afterProduction(
                        ciPrep.orElse(null), app, prodEval, multi, aggCredit);
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
                var prodEval = underwritingEvaluationService.record(app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults, app);
                creditIntelligenceFoundationService.afterProduction(
                        ciPrep.orElse(null), app, prodEval, multi, aggCredit);
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
                limitSizingService.applySanctionCapIfConfigured(app);
                app = applicationRepository.save(app);
                var prodEval = underwritingEvaluationService.record(app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults, app);
                creditIntelligenceFoundationService.afterProduction(
                        ciPrep.orElse(null), app, prodEval, multi, aggCredit);
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
            var prodEval = underwritingEvaluationService.record(app.getId(), multi, ctx, evaluatedByUserId, scorecardForRecord, paramResults, app);
            creditIntelligenceFoundationService.afterProduction(
                    ciPrep.orElse(null), app, prodEval, multi, aggCredit);
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
            limitSizingService.applySanctionCapIfConfigured(app);
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
        var legacyProdEval = underwritingEvaluationService.record(
                app.getId(), legacyMulti, ctx, evaluatedByUserId, null, null, app);
        creditIntelligenceFoundationService.afterProduction(
                ciPrep.orElse(null), app, legacyProdEval, legacyMulti, decision.decision());
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
        attachLimitSizingPolicy(app, ctx, meta);
        fi.put("underwritingMeta", meta);
        app.setFinancialInfo(fi);
    }

    /** Persist rule-configured limit sizing snapshot for CAM / UI (no status change). */
    private void attachLimitSizingPolicy(
            LoanApplication app, EffectiveUnderwritingContext ctx, Map<String, Object> meta) {
        Map<String, BigDecimal> scorecardHint = ctx != null ? ctx.scorecard() : null;
        limitSizingService.computeForApp(app, scorecardHint).ifPresent(result -> {
            Map<String, Object> policy = limitSizingService.toMetaMap(result);
            meta.put("limitSizingPolicy", policy);
            meta.put("scfLimitPolicy", policy);
        });
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
            limitSizingService.applySanctionCapIfConfigured(app);
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
        java.util.UUID approver = null;
        if (approvedByUserId != null && !approvedByUserId.isBlank()) {
            try {
                approver = java.util.UUID.fromString(approvedByUserId.trim());
            } catch (IllegalArgumentException ignored) {
                // optional header — ignore invalid uuid
            }
        }
        workflowRoleGuard.requireChecker(approver);
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.MARK_CAM_REVIEWED);
        LoanApplication app = findOrThrow(applicationId);
        boolean appCamReady =
                app.getStatus() == ApplicationStatus.CAM_READY || app.getStatus() == ApplicationStatus.APPROVED;
        if (!appCamReady && !creditAppraisalService.isCamAwaitingManagerReview(applicationId)) {
            throw new BusinessRuleException(
                    "CAM review is only allowed from CAM_READY (or legacy APPROVED), or when CAM is SUBMITTED. Current: "
                            + app.getStatus(),
                    "CAM_REVIEW_INVALID_STATUS",
                    "OPEN_CAM",
                    Map.of("status", app.getStatus().name()));
        }
        creditAppraisalService.markReviewed(applicationId, approver);
        creditAppraisalService.applyCamSanctionBasisToApplication(applicationId);
        app = findOrThrow(applicationId);
        app.setStatus(ApplicationStatus.CAM_REVIEWED);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "CAM_REVIEWED", null, Map.of("status", app.getStatus().name()),
                Map.of("status", "CAM_REVIEWED"),
                "Credit manager marked CAM as reviewed");
        try {
            plpAnchorSanctionHookService.onAnchorApplicationCamReviewed(applicationId);
        } catch (Exception e) {
            log.error("[PLP-ANCHOR-CAM] PLP anchor sync failed for {} — CAM review is preserved: {}",
                    app.getApplicationNumber(), e.getMessage(), e);
        }
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse markReadyForDisbursement(UUID applicationId) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.READY_FOR_DISBURSEMENT);
        LoanApplication app = findOrThrow(applicationId);
        if (invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)) {
            throw new BusinessRuleException(
                    "Invoice discounting borrower onboarding is complete after sanction and eSign — no term loan disbursement applies.",
                    "INVOICE_DISCOUNTING_NO_TERM_LOAN",
                    "READY_FOR_DISBURSEMENT",
                    Map.of("status", app.getStatus().name()));
        }
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
    public Map<String, Object> sanctionApplication(UUID applicationId, Map<String, Object> sanctionParams, UUID actorUserId) {
        vkycWorkflowService.assertVkycCleared(applicationId, VkycWorkflowService.DownstreamAction.SANCTION);
        LoanApplication app = findOrThrow(applicationId);
        boolean anchorFlow = InvoiceDiscountingApplicationRules.isAnchorFlow(app);
        boolean idBorrowerFlow = invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app);
        boolean skipLms = idBorrowerFlow || InvoiceDiscountingApplicationRules.isAnchorFlow(app);
        boolean skipKfs = InvoiceDiscountingApplicationRules.skipsKfsAtSanction(app);

        workflowRoleGuard.requireL2Sanction(actorUserId);

        if (anchorFlow) {
            ProgramMaster program = invoiceDiscountingSanctionDefaultsService.resolveAnchorProgram(app)
                    .orElseThrow(() -> new BusinessRuleException(
                            "Complete PLP program setup before anchor sanction",
                            "PROGRAM_REQUIRED",
                            "ANCHOR_SANCTION",
                            null));
            if (program.getApprovalStatus() != ProgramApprovalStatus.APPROVED) {
                throw new BusinessRuleException(
                        "Program must be approved in PLP before anchor sanction. Approve in PLP workbench "
                                + "(CREDIT_ANALYST → CREDIT_MANAGER), then refresh PLP status on LOS. Current: "
                                + program.getApprovalStatus()
                                + (program.getPlpOperationalStatus() != null
                                        ? " (PLP: " + program.getPlpOperationalStatus() + ")"
                                        : ""),
                        "PROGRAM_NOT_APPROVED",
                        "PROGRAM_APPROVAL",
                        Map.of("approvalStatus", program.getApprovalStatus().name()));
            }
            if (app.getStatus() != ApplicationStatus.SANCTION_PENDING) {
                throw new BusinessRuleException(
                        "Anchor sanction requires SANCTION_PENDING after due diligence approval. Current: "
                                + app.getStatus(),
                        "STATUS_NOT_SANCTION_PENDING",
                        "ANCHOR_DUE_DILIGENCE",
                        Map.of("status", app.getStatus().name()));
            }
        } else if (app.getStatus() != ApplicationStatus.CAM_REVIEWED
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

        if (anchorFlow) {
            invoiceDiscountingSanctionDefaultsService.applyAnchorProgramDefaults(app);
        } else {
            creditAppraisalService.applyCamSanctionBasisToApplication(applicationId);
            app = findOrThrow(applicationId);
        }

        if (app.getSanctionedAmount() == null) {
            app.setSanctionedAmount(app.getRequestedAmount());
        }
        limitSizingService.applySanctionCapIfConfigured(app);
        if (app.getApprovedRate() == null && app.getInterestRate() != null) {
            app.setApprovedRate(app.getInterestRate());
        }
        if (app.getInterestRate() == null && app.getApprovedRate() != null) {
            app.setInterestRate(app.getApprovedRate());
        }
        if (!anchorFlow && app.getTenureMonths() == null) {
            throw new BusinessRuleException("Tenure is required for sanction", "TENURE_REQUIRED", "SANCTION", null);
        }

        BigDecimal sanctionRate = app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        int sanctionTenure;
        if (anchorFlow) {
            if (sanctionRate == null) {
                throw new BusinessRuleException(
                        "Anchor sanction requires a program interest rate. Complete PLP program setup with rate, "
                                + "or enter terms before sanction.",
                        "INTEREST_RATE_REQUIRED",
                        "SANCTION",
                        null);
            }
            sanctionTenure = app.getTenureMonths() != null
                    ? app.getTenureMonths()
                    : invoiceDiscountingSanctionDefaultsService.defaultAnchorTenureMonths(app);
            app.setTenureMonths(sanctionTenure);
            app.setApprovedRate(sanctionRate);
            app.setInterestRate(sanctionRate);
        } else {
            if (sanctionRate == null) {
                throw new BusinessRuleException(
                        "Interest rate is required for sanction. Capture it on the CAM tab or enter at sanction.",
                        "INTEREST_RATE_REQUIRED",
                        "SANCTION",
                        null);
            }
            sanctionTenure = app.getTenureMonths();
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

        boolean anchorTermsEsign = anchorFlow && InvoiceDiscountingApplicationRules.requiresProgramTermsEsign(app);
        if (!anchorTermsEsign) {
            app.setStatus(ApplicationStatus.SANCTIONED);
        }
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        SanctionRecord rec = SanctionRecord.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .approvedAmount(app.getSanctionedAmount())
                .approvedTenure(sanctionTenure)
                .interestRate(sanctionRate)
                .processingFee(fee)
                .conditionsText(conditions)
                .remarks(remarks)
                .approvedBy(approvedBy)
                .build();
        sanctionRecordRepository.save(rec);

        KfsDocument kfs = null;
        String lmsReferenceId = null;
        if (skipKfs && anchorTermsEsign) {
            Map<String, Object> programMeta = buildAnchorProgramMeta(app);
            kfs = kfsService.generateAnchorProgramTermsDocument(applicationId, rec, programMeta);
            app.setStatus(ApplicationStatus.ESIGN_PENDING);
            app = applicationRepository.save(app);
            auditService.logEvent(applicationId, "FLOW", "ANCHOR_PROGRAM_TERMS_ESIGN",
                    null, Map.of("status", ApplicationStatus.SANCTION_PENDING.name()),
                    Map.of("status", "ESIGN_PENDING", "kfsId", kfs.getId().toString()),
                    "Anchor program terms generated — awaiting anchor eSign");
            log.info("Anchor program terms for {} — awaiting eSign, kfs={}",
                    app.getApplicationNumber(), kfs.getId());
        } else if (skipKfs) {
            auditService.logEvent(applicationId, "FLOW", "SANCTION",
                    null, Map.of("status", app.getStatus().name()),
                    Map.of("status", "SANCTIONED", "sanctionedAmount", app.getSanctionedAmount().toString(),
                            "invoiceDiscountingFlow", "ANCHOR"),
                    "Anchor onboarded — sanction complete (no LMS loan or KFS)");
            log.info("Invoice discounting anchor sanction for {} — amount: {}",
                    app.getApplicationNumber(), app.getSanctionedAmount());
        } else if (idBorrowerFlow) {
            Map<String, Object> charges = new LinkedHashMap<>();
            if (sanctionParams != null) {
                charges.putAll(sanctionParams);
            }
            kfs = kfsService.generateInvoiceDiscountingBorrowerTermsDocument(applicationId, rec, charges);
            app.setStatus(ApplicationStatus.KFS_GENERATED);
            app = applicationRepository.save(app);
            auditService.logEvent(applicationId, "FLOW", "SANCTION_TERMS",
                    null, Map.of("status", "SANCTIONED"),
                    Map.of("status", "KFS_GENERATED", "sanctionedAmount", app.getSanctionedAmount().toString(),
                            "kfsId", kfs.getId().toString(), "documentKind", "INVOICE_DISCOUNTING_TERMS"),
                    "Invoice discounting borrower sanctioned — terms document generated (no LMS loan)");
            log.info("Invoice discounting borrower sanction for {} — terms doc {}, status KFS_GENERATED",
                    app.getApplicationNumber(), kfs.getId());
        } else {
            Map<String, Object> charges = new LinkedHashMap<>();
            if (sanctionParams != null) {
                charges.putAll(sanctionParams);
            }
            lmsService.appendPreOpenEncoreSummaryForKfsIfEnabled(app, charges);
            kfs = kfsService.generateKfs(applicationId, charges);
            app.setStatus(ApplicationStatus.KFS_GENERATED);
            app = applicationRepository.save(app);

            auditService.logEvent(applicationId, "FLOW", "SANCTION_KFS",
                    null, Map.of("status", "CAM_REVIEWED"),
                    Map.of("status", "KFS_GENERATED", "sanctionedAmount", app.getSanctionedAmount().toString(),
                            "kfsId", kfs.getId().toString()),
                    "Sanction recorded and KFS generated");

            log.info("Sanction+KFS for {} — amount: {}, rate: {}%, KFS: {}",
                    app.getApplicationNumber(), app.getSanctionedAmount(), app.getApprovedRate(), kfs.getId());

            if (!skipLms) {
            log.info("[DEBUG-ca1703] sanctionApplication triggering LMS for app={}", app.getApplicationNumber());
            try {
                lmsReferenceId = lmsService.createLmsAccountOnSanction(app);
                if (lmsReferenceId != null) {
                    app.setLmsReferenceId(lmsReferenceId);
                    app = applicationRepository.save(app);
                    lmsService.attachHandoverScheduleToKfs(app.getApplicationNumber(), kfs.getId());
                    lmsService.reconcileKfsFromEncoreAfterSanction(app, kfs.getId(), lmsReferenceId);
                    log.info("[DEBUG-ca1703] LMS account created successfully: lmsRef={} for app={}",
                            lmsReferenceId, app.getApplicationNumber());
                }
            } catch (Exception lmsEx) {
                log.error("[LMS-SANCTION] LMS integration failed for {} — sanction is preserved: {}",
                        app.getApplicationNumber(), lmsEx.getMessage(), lmsEx);
            }
            }
        }

        if (!anchorFlow) {
            try {
                LoanApplication plpSynced = plpSanctionSyncOrchestrator.syncAfterSanction(applicationId);
                PlpApplicationSyncFieldMerge.mergeInto(app, plpSynced);
            } catch (Exception plpEx) {
                log.error("[PLP-SANCTION] PLP integration failed for {} — sanction is preserved: {}",
                        app.getApplicationNumber(), plpEx.getMessage(), plpEx);
            }
        }

        if (anchorFlow && app.getStatus() == ApplicationStatus.SANCTIONED) {
            try {
                plpAnchorSanctionHookService.onAnchorApplicationSanctioned(applicationId);
            } catch (Exception anchorPlpEx) {
                log.error("[PLP-ANCHOR-SANCTION] PLP anchor sync failed for {} — sanction is preserved: {}",
                        app.getApplicationNumber(), anchorPlpEx.getMessage(), anchorPlpEx);
            }
        }

        if (!anchorTermsEsign || app.getStatus() == ApplicationStatus.SANCTIONED) {
            try {
                sanctionApprovedNotifier.publishSanctionApprovedEmail(app, rec, anchorFlow, idBorrowerFlow, kfs);
            } catch (Exception emailEx) {
                log.error("[SANCTION_EMAIL] notification failed for {} — sanction is preserved: {}",
                        app.getApplicationNumber(), emailEx.getMessage(), emailEx);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", applicationId);
        result.put("applicationNumber", app.getApplicationNumber());
        result.put("status", app.getStatus().name());
        result.put("sanctionedAmount", app.getSanctionedAmount());
        result.put("interestRate", app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate());
        result.put("tenureMonths", app.getTenureMonths());
        result.put("skipLmsKfs", skipKfs || idBorrowerFlow);
        result.put("skipLms", skipLms);
        result.put("invoiceDiscountingBorrower", idBorrowerFlow);
        result.put("kfsId", kfs != null ? kfs.getId() : "");
        result.put("kfsVersion", kfs != null ? kfs.getVersion() : "");
        result.put("kfsStatus", kfs != null ? kfs.getStatus() : "");
        result.put("coolingOffHours", kfs != null ? kfs.getCoolingOffHours() : "");
        result.put("lmsReferenceId", lmsReferenceId != null ? lmsReferenceId : "");
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
        boolean anchorFlow = InvoiceDiscountingApplicationRules.isAnchorFlow(app);
        boolean idBorrowerFlow = invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app);
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

        app.setStatus(anchorFlow ? ApplicationStatus.SANCTIONED : ApplicationStatus.ESIGN_COMPLETED);
        if (esignTransactionId != null) {
            app.setEsignTransactionId(esignTransactionId);
        }
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        try {
            esignSignedApplicationDocumentService.registerSignedDocumentsFromKfs(applicationId);
        } catch (Exception e) {
            log.warn("[eSign] Failed to register signed application documents for {}: {}",
                    applicationId, e.getMessage());
        }

        if (anchorFlow) {
            try {
                plpAnchorSanctionHookService.onAnchorApplicationSanctioned(applicationId);
            } catch (Exception e) {
                log.error("[PLP-ANCHOR-SANCTION] after eSign failed for {}: {}",
                        app.getApplicationNumber(), e.getMessage(), e);
            }
            // Welcome replaces the overlapping post-esign sanction email for anchors.
            try {
                welcomeOnboardingNotifier.sendWelcomeAfterEsignComplete(app, true, false);
            } catch (Exception e) {
                log.error("[WELCOME_EMAIL] anchor failed: {}", e.getMessage());
            }
        } else if (idBorrowerFlow) {
            try {
                anchorKfsSignedNotifier.notifyAnchorAfterBorrowerKfsSigned(applicationId);
            } catch (Exception e) {
                log.error("[ANCHOR_KFS_EMAIL] failed for {}: {}", app.getApplicationNumber(), e.getMessage());
            }
            try {
                welcomeOnboardingNotifier.sendWelcomeAfterEsignComplete(app, false, true);
            } catch (Exception e) {
                log.error("[WELCOME_EMAIL] ID borrower failed: {}", e.getMessage());
            }
        } else {
            try {
                welcomeOnboardingNotifier.sendWelcomeAfterEsignComplete(app, false, false);
            } catch (Exception e) {
                log.error("[WELCOME_EMAIL] term borrower failed: {}", e.getMessage());
            }
        }

        auditService.logEvent(applicationId, "FLOW", "ESIGN_COMPLETE",
                null, Map.of("status", "ESIGN_PENDING"),
                Map.of("status", app.getStatus().name(), "esignTransactionId",
                        esignTransactionId != null ? esignTransactionId : ""),
                anchorFlow
                        ? "Anchor program terms signed — onboarding complete (SANCTIONED)"
                        : (idBorrowerFlow
                                ? "Invoice discounting borrower terms signed — program onboarding complete"
                                : "eSign completed — ready for disbursement"));

        log.info("eSign completed for {} — status: {}", app.getApplicationNumber(), app.getStatus());
        return toResponse(app);
    }

    private Map<String, Object> buildAnchorProgramMeta(LoanApplication app) {
        Map<String, Object> meta = new LinkedHashMap<>();
        invoiceDiscountingSanctionDefaultsService.resolveAnchorProgram(app).ifPresent(program -> {
            meta.put("programName", program.getProgramName());
            meta.put("programCode", program.getProgramCode());
            meta.put("productType", program.getProductType());
            meta.put("programLimit", program.getProgramLimit());
            meta.put("maxBorrowerLimit", program.getMaxBorrowerLimit());
            meta.put("interestRate", program.getInterestRate());
            meta.put("tenureDays", program.getTenureDays());
            meta.put("lmsEntryIn", program.getLmsEntryIn());
            if (program.getValidityStartDate() != null || program.getValidityEndDate() != null) {
                meta.put("validity",
                        String.valueOf(program.getValidityStartDate()) + " to "
                                + String.valueOf(program.getValidityEndDate()));
            }
        });
        return meta;
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
            Map<String, Object> sanctionResult = self.sanctionApplication(applicationId, sanctionParams, null);
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

    @Transactional(readOnly = true)
    public Map<String, Object> getManualOverrideEligibility(
            UUID applicationId,
            String processCode,
            String failureCode,
            Set<String> userRoles) {
        LoanApplication app = findOrThrow(applicationId);
        WorkflowConfig config = activeWorkflowConfigService.findActiveForApplication(app).orElse(null);
        Map<String, Object> policy = resolveManualOverridePolicy(config, processCode, failureCode);
        if (policy == null) {
            return Map.of(
                    "canOverride", false,
                    "overrideAllowed", false,
                    "reason", "No active override policy for this process/failure"
            );
        }
        boolean allowed = boolPolicy(policy, "override_allowed", true);
        if (!allowed) {
            return Map.of("canOverride", false, "overrideAllowed", false, "reason", "Override disabled by policy");
        }
        Set<String> allowedRoles = allowedRoles(policy);
        Set<String> normalizedRoles = normalizeRoles(userRoles);
        boolean roleAllowed = allowedRoles.isEmpty() || normalizedRoles.stream().anyMatch(allowedRoles::contains);
        if (!roleAllowed) {
            return Map.of(
                    "canOverride", false,
                    "overrideAllowed", true,
                    "reason", "User role not allowed",
                    "allowedRoles", allowedRoles
            );
        }
        List<String> allowedFromStatuses = textList(policy, "allowed_from_statuses");
        if (!allowedFromStatuses.isEmpty() && !allowedFromStatuses.contains(app.getStatus().name())) {
            return Map.of(
                    "canOverride", false,
                    "overrideAllowed", true,
                    "reason", "Application status not eligible for override",
                    "allowedFromStatuses", allowedFromStatuses,
                    "currentStatus", app.getStatus().name()
            );
        }
        boolean requiresScore = "UNDERWRITING".equals(normalize(processCode));
        return Map.of(
                "canOverride", true,
                "overrideAllowed", true,
                "requiresReason", boolPolicy(policy, "requires_reason", true),
                "requiresRemarks", boolPolicy(policy, "requires_remarks", false),
                "requiresApproval", boolPolicy(policy, "requires_approval", false),
                "requiresScore", requiresScore,
                "allowedRoles", allowedRoles,
                "targetStatus", strPolicy(policy, "override_to_status", ""),
                "policy", policy
        );
    }

    @Transactional
    public Map<String, Object> applyManualOverride(
            UUID applicationId,
            String processCode,
            String failureCode,
            String overrideReason,
            String remarks,
            String approvalReference,
            Integer manualBureauScore,
            Integer creditRiskScore,
            UUID overrideBy,
            Set<String> userRoles) {
        LoanApplication app = findOrThrow(applicationId);
        WorkflowConfig config = activeWorkflowConfigService.findActiveForApplication(app).orElse(null);
        Map<String, Object> policy = resolveManualOverridePolicy(config, processCode, failureCode);
        if (policy == null || !boolPolicy(policy, "override_allowed", true)) {
            throw new BusinessRuleException("Override is not allowed for this process/failure");
        }
        Set<String> allowedRoles = allowedRoles(policy);
        Set<String> normalizedRoles = normalizeRoles(userRoles);
        if (!allowedRoles.isEmpty() && normalizedRoles.stream().noneMatch(allowedRoles::contains)) {
            throw new BusinessRuleException("Forbidden: user role is not allowed to override this failure");
        }
        if (boolPolicy(policy, "requires_reason", true) && (overrideReason == null || overrideReason.isBlank())) {
            throw new BusinessRuleException("Override reason is mandatory");
        }
        if (boolPolicy(policy, "requires_remarks", false) && (remarks == null || remarks.isBlank())) {
            throw new BusinessRuleException("Override remarks are mandatory");
        }
        if (boolPolicy(policy, "requires_approval", false) && (approvalReference == null || approvalReference.isBlank())) {
            throw new BusinessRuleException("Approval reference is mandatory for this override");
        }
        String normalizedProcess = normalize(processCode);
        String normalizedFailure = normalize(failureCode);
        if ("UNDERWRITING".equals(normalizedProcess)
                && (manualBureauScore == null || manualBureauScore <= 0)) {
            throw new BusinessRuleException(
                    "Updated bureau/credit score is mandatory for underwriting manual override");
        }
        List<String> allowedFromStatuses = textList(policy, "allowed_from_statuses");
        if (!allowedFromStatuses.isEmpty() && !allowedFromStatuses.contains(app.getStatus().name())) {
            throw new BusinessRuleException("Override not allowed from current application status: " + app.getStatus());
        }

        ApplicationStatus previousStatus = app.getStatus();
        ApplicationStatus targetStatus = determineTargetStatus(app, policy, processCode);
        VkycStatus previousVkycStatus = app.getVkycStatus();
        boolean vkycBypassed = false;
        if ("VKYC".equals(normalize(processCode)) || "VIDEO_KYC".equals(normalize(processCode))) {
            app.setVkycStatus(VkycStatus.AUDITOR_APPROVED);
            app.setVkycCompletedAt(Instant.now());
            vkycBypassed = true;
        }
        if (targetStatus != null && targetStatus != previousStatus) {
            app.setStatus(targetStatus);
        }
        if ("KYC".equals(normalizedProcess) && "KYC_FAILED".equals(normalizedFailure)) {
            creditControlService.applyManualKycPassOnProcessOverride(app, remarks);
        }
        if ("UNDERWRITING".equals(normalizedProcess)) {
            completeUnderwritingAfterManualOverride(app, manualBureauScore, creditRiskScore, remarks);
        }

        Map<String, Object> fi = app.getFinancialInfo() != null ? new LinkedHashMap<>(app.getFinancialInfo()) : new LinkedHashMap<>();
        List<Map<String, Object>> history = new ArrayList<>();
        Object existing = fi.get("manualOverrides");
        if (existing instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) map;
                    history.add(new LinkedHashMap<>(cast));
                }
            }
        }
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("timestamp", Instant.now().toString());
        marker.put("processCode", normalize(processCode));
        marker.put("failureCode", normalize(failureCode));
        marker.put("reason", overrideReason != null ? overrideReason.trim() : "");
        marker.put("remarks", remarks != null ? remarks.trim() : "");
        marker.put("previousStatus", previousStatus.name());
        marker.put("newStatus", app.getStatus().name());
        marker.put("overriddenBy", overrideBy != null ? overrideBy.toString() : "");
        marker.put("overrideApplied", "true");
        marker.put("vkycBypassed", vkycBypassed ? "true" : "false");
        if (manualBureauScore != null && manualBureauScore > 0) {
            marker.put("manualBureauScore", manualBureauScore);
        }
        if (app.getCreditRiskScore() != null) {
            marker.put("creditRiskScore", app.getCreditRiskScore());
        }
        history.add(marker);
        fi.put("manualOverrides", history);
        fi.put("manualOverrideFlag", "MANUALLY_OVERRIDDEN");
        app.setFinancialInfo(fi);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);

        processOverrideHistoryRepository.save(ProcessOverrideHistory.builder()
                .applicationId(applicationId)
                .processCode(normalize(processCode))
                .failureCode(normalize(failureCode))
                .previousStatus(previousStatus.name())
                .newStatus(app.getStatus().name())
                .overrideReason(overrideReason != null ? overrideReason.trim() : "")
                .remarks(remarks != null ? remarks.trim() : null)
                .approvalReference(approvalReference != null ? approvalReference.trim() : null)
                .overriddenBy(overrideBy)
                .build());

        auditService.logEvent(
                applicationId,
                "PROCESS_OVERRIDE",
                "MANUAL_OVERRIDE_APPLIED",
                overrideBy,
                Map.of(
                        "processCode", normalize(processCode),
                        "failureCode", normalize(failureCode),
                        "status", previousStatus.name(),
                        "vkycStatus", previousVkycStatus != null ? previousVkycStatus.name() : ""
                ),
                Map.of(
                        "status", app.getStatus().name(),
                        "overrideFlag", "MANUALLY_OVERRIDDEN",
                        "overrideReason", overrideReason != null ? overrideReason : "",
                        "remarks", remarks != null ? remarks : "",
                        "approvalReference", approvalReference != null ? approvalReference : "",
                        "vkycStatus", app.getVkycStatus() != null ? app.getVkycStatus().name() : ""
                ),
                "Manual process override applied"
        );

        return Map.of(
                "applicationId", app.getId().toString(),
                "processCode", normalize(processCode),
                "failureCode", normalize(failureCode),
                "previousStatus", previousStatus.name(),
                "newStatus", app.getStatus().name(),
                "overrideApplied", true
        );
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
                .vkycCompletionMode(app.getVkycCompletionMode())
                .pkycReason(app.getPkycReason())
                .pkycComments(app.getPkycComments())
                .pkycDocumentId(app.getPkycDocumentId())
                .pkycVerifiedBy(app.getPkycVerifiedBy())
                .pkycVerifiedAt(app.getPkycVerifiedAt())
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

    private ApplicationStatus determineTargetStatus(LoanApplication app, Map<String, Object> policy, String processCode) {
        String explicitTarget = strPolicy(policy, "override_to_status", "");
        if (!explicitTarget.isBlank()) {
            try {
                return ApplicationStatus.valueOf(explicitTarget.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new BusinessRuleException("Invalid override_to_status in policy: " + explicitTarget);
            }
        }
        if (app.getStatus() == ApplicationStatus.KYC_FAILED) {
            return ApplicationStatus.KYC_IN_PROGRESS;
        }
        if (app.getStatus() == ApplicationStatus.REJECTED && "UNDERWRITING".equals(normalize(processCode))) {
            return ApplicationStatus.CAM_READY;
        }
        return app.getStatus();
    }

    /**
     * After a controlled underwriting rejection override, treat the case as credit-approved and advance to CAM.
     */
    private void completeUnderwritingAfterManualOverride(
            LoanApplication app, Integer manualBureauScore, Integer creditRiskScore, String remarks) {
        app.setManualBureauScore(manualBureauScore);
        if (remarks != null && !remarks.isBlank()) {
            app.setManualBureauRemarks(remarks.trim());
        }
        int riskScore = creditRiskScore != null && creditRiskScore > 0 ? creditRiskScore : manualBureauScore;
        app.setCreditRiskScore(riskScore);
        app.setCreditDecision("APPROVED");
        if (app.getInterestRate() != null) {
            app.setApprovedRate(app.getInterestRate());
        }
        app.setSanctionedAmount(app.getRequestedAmount());
        limitSizingService.applySanctionCapIfConfigured(app);
        app.setStatus(ApplicationStatus.UNDERWRITING_COMPLETED);
        applicationRepository.save(app);
        creditAppraisalService.ensureCamForApplication(app);
        app.setStatus(ApplicationStatus.CAM_READY);
    }

    private Map<String, Object> resolveManualOverridePolicy(WorkflowConfig config, String processCode, String failureCode) {
        List<Map<String, Object>> raw = config != null ? config.getManualOverridePolicies() : null;
        List<Map<String, Object>> policies = raw != null ? raw : defaultOverridePolicies();
        String process = normalize(processCode);
        String failure = normalize(failureCode);
        for (Map<String, Object> p : policies) {
            if (!boolPolicy(p, "is_active", true)) continue;
            String pProcess = normalize(strPolicy(p, "process_code", ""));
            String pFailure = normalize(strPolicy(p, "failure_code", ""));
            boolean processMatch = pProcess.equals(process);
            boolean failureMatch = pFailure.equals("*") || pFailure.equals(failure);
            if (processMatch && failureMatch) return p;
        }
        return null;
    }

    private List<Map<String, Object>> defaultOverridePolicies() {
        List<Map<String, Object>> defaults = new ArrayList<>();
        defaults.add(new LinkedHashMap<>(Map.of(
                "process_code", "KYC",
                "failure_code", "KYC_FAILED",
                "override_allowed", true,
                "allowed_roles", List.of("ADMIN", "ADMINISTRATOR", "RISK_MANAGER", "CREDIT_MANAGER", "CREDIT_OFFICER"),
                "requires_reason", true,
                "requires_remarks", true,
                "requires_approval", false,
                "override_to_status", "KYC_IN_PROGRESS",
                "allowed_from_statuses", List.of("KYC_FAILED"),
                "is_active", true
        )));
        defaults.add(new LinkedHashMap<>(Map.of(
                "process_code", "VKYC",
                "failure_code", "*",
                "override_allowed", true,
                "allowed_roles", List.of("ADMIN", "ADMINISTRATOR", "RISK_MANAGER", "VKYC_MANAGER"),
                "requires_reason", true,
                "requires_remarks", true,
                "requires_approval", false,
                "allowed_from_statuses", List.of("KYC_IN_PROGRESS", "UNDERWRITING", "CAM_READY", "CAM_REVIEWED", "SANCTION_PENDING", "REJECTED"),
                "is_active", true
        )));
        defaults.add(new LinkedHashMap<>(Map.of(
                "process_code", "UNDERWRITING",
                "failure_code", "UNDERWRITING_REJECTED",
                "override_allowed", true,
                "allowed_roles", List.of("ADMIN", "ADMINISTRATOR", "RISK_MANAGER", "CREDIT_MANAGER", "CREDIT_OFFICER"),
                "requires_reason", true,
                "requires_remarks", true,
                "requires_approval", true,
                "override_to_status", "CAM_READY",
                "allowed_from_statuses", List.of("REJECTED"),
                "is_active", true
        )));
        return defaults;
    }

    private Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) return Set.of();
        Set<String> out = new HashSet<>();
        for (String role : roles) {
            String normalized = normalize(role);
            if (!normalized.isBlank()) out.add(normalized);
        }
        return out;
    }

    private Set<String> allowedRoles(Map<String, Object> policy) {
        Set<String> out = new HashSet<>();
        List<String> configured = textList(policy, "allowed_roles");
        if (configured.isEmpty()) return DEFAULT_OVERRIDE_ROLES;
        for (String role : configured) {
            String normalized = normalize(role);
            if (!normalized.isBlank()) out.add(normalized);
        }
        return out;
    }

    private List<String> textList(Map<String, Object> policy, String keySnakeCase) {
        Object val = readPolicy(policy, keySnakeCase);
        if (!(val instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (!s.isBlank()) out.add(normalize(s));
        }
        return out;
    }

    private boolean boolPolicy(Map<String, Object> policy, String keySnakeCase, boolean defaultValue) {
        Object val = readPolicy(policy, keySnakeCase);
        if (val == null) return defaultValue;
        if (val instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(val));
    }

    private String strPolicy(Map<String, Object> policy, String keySnakeCase, String defaultValue) {
        Object val = readPolicy(policy, keySnakeCase);
        if (val == null) return defaultValue;
        String s = String.valueOf(val).trim();
        return s.isBlank() ? defaultValue : s;
    }

    private Object readPolicy(Map<String, Object> policy, String keySnakeCase) {
        if (policy == null) return null;
        Object v = policy.get(keySnakeCase);
        if (v != null) return v;
        String camel = toCamelCase(keySnakeCase);
        return policy.get(camel);
    }

    private String toCamelCase(String snake) {
        String[] parts = snake.split("_");
        if (parts.length == 0) return snake;
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT)).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
