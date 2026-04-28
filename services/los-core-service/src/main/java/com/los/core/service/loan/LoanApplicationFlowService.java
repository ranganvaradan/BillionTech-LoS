package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.CreditDecisionServiceImpl;
import com.los.core.service.credit.ICreditDecisionService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.integration.LmsAdapterClient;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.kyc.IKycOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final KycStepResultRepository kycStepResultRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final IIntegrationRouterService integrationRouter;
    private final ICreditDecisionService creditDecisionService;
    private final KfsService kfsService;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final LmsAdapterClient lmsAdapterClient;
    private final AuditService auditService;

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
        LoanApplication app = findOrThrow(applicationId);
        assertStatus(app, ApplicationStatus.KYC_IN_PROGRESS, "run KYC");

        List<KycStepResultResponse> results = kycOrchestrationService.executeWorkflow(applicationId, kycPayload);

        long successCount = results.stream().filter(r -> r.getOutcome() == StepOutcome.SUCCESS).count();
        long failureCount = results.stream().filter(r -> r.getOutcome() == StepOutcome.FAILURE).count();
        boolean allPassed = failureCount == 0;

        if (!allPassed) {
            app.setStatus(ApplicationStatus.KYC_FAILED);
            applicationRepository.save(app);
            log.warn("KYC workflow for {} — {} failures out of {} steps",
                    app.getApplicationNumber(), failureCount, results.size());
        }

        return Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "totalSteps", results.size(),
                "successCount", successCount,
                "failureCount", failureCount,
                "allPassed", allPassed,
                "results", results
        );
    }

    // ========================== STEP 3: BUREAU PULL ==========================

    /**
     * Pull credit bureau report (Equifax) for the application.
     * Requires KYC_IN_PROGRESS status. Stores bureau score on the application.
     */
    @Transactional
    public Map<String, Object> pullBureauReport(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        Map<String, Object> kycOutcome = kycOrchestrationService.computeKycOutcome(applicationId);
        String outcome = String.valueOf(kycOutcome.getOrDefault("outcome", "INCOMPLETE"));
        if (!"PASS".equalsIgnoreCase(outcome)) {
            @SuppressWarnings("unchecked")
            Object stepSummary = kycOutcome.getOrDefault("stepSummary", List.of());

            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "BUREAU_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "KYC_OUTCOME_NOT_PASS", "action", "BUREAU_PULL", "kycOutcome", outcome, "stepSummary", stepSummary),
                    null,
                    "Bureau pull blocked: KYC outcome is " + outcome);
            throw new BusinessRuleException(
                    "Bureau pull blocked: KYC outcome is " + outcome,
                    "KYC_OUTCOME_NOT_PASS",
                    "BUREAU_PULL",
                    Map.of("status", app.getStatus().name(), "kycOutcome", outcome, "stepSummary", stepSummary)
            );
        }

        // Build borrower info from personalInfo JSONB
        Map<String, Object> borrowerInfo = new HashMap<>();
        if (app.getPersonalInfo() != null) {
            borrowerInfo.putAll(app.getPersonalInfo());
        }
        borrowerInfo.put("applicationId", applicationId.toString());
        borrowerInfo.put("applicationNumber", app.getApplicationNumber());

        IIntegrationRouterService.BureauRouteResult bureauResult = integrationRouter.routeBureauPull(borrowerInfo);

        // Store as a KYC step result
        KycStepResult stepResult = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.BUREAU_PULL)
                .provider(com.los.core.model.enums.ProviderType.EQUIFAX)
                .outcome(bureauResult.success() ? StepOutcome.SUCCESS : StepOutcome.FAILURE)
                .confidenceScore(bureauResult.success() ? 0.95 : 0.0) // creditScore (e.g. 720) stored on app.bureauScore, not here
                .parsedData(bureauResult.reportData())
                .transactionId(bureauResult.transactionId())
                .errorMessage(bureauResult.errorMessage())
                .attemptNumber(1)
                .completedAt(Instant.now())
                .build();
        kycStepResultRepository.save(stepResult);

        // Update bureau score on the application
        if (bureauResult.success()) {
            app.setBureauScore(bureauResult.creditScore());
            applicationRepository.save(app);
        }

        auditService.logEvent(applicationId, "BUREAU_PULL",
                Map.of("success", bureauResult.success(), "creditScore", bureauResult.creditScore(),
                        "transactionId", bureauResult.transactionId() != null ? bureauResult.transactionId() : ""));

        log.info("Bureau pull for {} — score: {}, success: {}",
                app.getApplicationNumber(), bureauResult.creditScore(), bureauResult.success());

        return Map.of(
                "applicationId", applicationId,
                "success", bureauResult.success(),
                "creditScore", bureauResult.creditScore(),
                "transactionId", bureauResult.transactionId() != null ? bureauResult.transactionId() : "",
                "reportData", bureauResult.reportData() != null ? bureauResult.reportData() : Map.of()
        );
    }

    // ========================== STEP 4: UNDERWRITING ==========================

    /**
     * Move application to UNDERWRITING and run credit decision engine.
     * Requires KYC_IN_PROGRESS status with at least some successful KYC steps.
     */
    @Transactional
    public Map<String, Object> underwriteApplication(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        Map<String, Object> kycOutcome = kycOrchestrationService.computeKycOutcome(applicationId);
        String outcome = String.valueOf(kycOutcome.getOrDefault("outcome", "INCOMPLETE"));
        if (!"PASS".equalsIgnoreCase(outcome)) {
            @SuppressWarnings("unchecked")
            Object stepSummary = kycOutcome.getOrDefault("stepSummary", List.of());

            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "UNDERWRITING_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "KYC_OUTCOME_NOT_PASS", "action", "UNDERWRITE", "kycOutcome", outcome, "stepSummary", stepSummary),
                    null,
                    "Underwriting blocked: KYC outcome is " + outcome);
            throw new BusinessRuleException(
                    "Underwriting blocked: KYC outcome is " + outcome,
                    "KYC_OUTCOME_NOT_PASS",
                    "UNDERWRITE",
                    Map.of("status", app.getStatus().name(), "kycOutcome", outcome, "stepSummary", stepSummary)
            );
        }

        if (app.getBureauScore() == null || app.getBureauScore() <= 0) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "UNDERWRITING_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "BUREAU_SCORE_MISSING_OR_NON_POSITIVE", "action", "UNDERWRITE", "bureauScore", app.getBureauScore() != null ? app.getBureauScore() : 0),
                    null,
                    "Underwriting blocked: bureau score missing");
            throw new BusinessRuleException(
                    "Underwriting blocked: bureau score is missing. Pull bureau first.",
                    "BUREAU_SCORE_MISSING_OR_NON_POSITIVE",
                    "UNDERWRITE",
                    Map.of("status", app.getStatus().name(), "bureauScore", app.getBureauScore() != null ? app.getBureauScore() : 0)
            );
        }

        // Transition to UNDERWRITING
        app.setStatus(ApplicationStatus.UNDERWRITING);
        app.setCurrentStepStartedAt(Instant.now());
        applicationRepository.save(app);

        // Run credit decision engine
        ICreditDecisionService.CreditDecisionResult decision = creditDecisionService.evaluate(applicationId);

        // Store decision on the application
        app.setCreditDecision(decision.decision());
        app.setCreditRiskScore(decision.riskScore());

        if ("APPROVED".equals(decision.decision()) || "APPROVED_WITH_CONDITIONS".equals(decision.decision())) {
            app.setStatus(ApplicationStatus.APPROVED);
            if (decision.recommendedRate() != null) {
                app.setApprovedRate(decision.recommendedRate());
            } else if (app.getInterestRate() != null) {
                app.setApprovedRate(app.getInterestRate());
            }
            app.setSanctionedAmount(app.getRequestedAmount());
        } else {
            app.setStatus(ApplicationStatus.REJECTED);
        }

        app = applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "UNDERWRITING_COMPLETE",
                null, Map.of("status", "UNDERWRITING"),
                Map.of("status", app.getStatus().name(), "decision", decision.decision(),
                        "riskScore", decision.riskScore()),
                "Credit decision: " + decision.decision());

        log.info("Underwriting for {} — decision: {}, risk score: {}, new status: {}",
                app.getApplicationNumber(), decision.decision(), decision.riskScore(), app.getStatus());

        return Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "decision", decision.decision(),
                "riskScore", decision.riskScore(),
                "creditScore", decision.creditScore(),
                "reasons", decision.reasons(),
                "conditions", decision.conditions(),
                "recommendedRate", decision.recommendedRate() != null ? decision.recommendedRate() : "",
                "sanctionedAmount", app.getSanctionedAmount() != null ? app.getSanctionedAmount() : ""
        );
    }

    // ========================== STEP 5: SANCTION + KFS ==========================

    /**
     * Issue sanction letter, generate KFS, and transition to SANCTION_ISSUED.
     * Optionally allows overriding sanctioned amount and rate.
     */
    @Transactional
    public Map<String, Object> sanctionApplication(UUID applicationId, Map<String, Object> sanctionParams) {
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "SANCTION_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "STATUS_NOT_APPROVED", "action", "SANCTION"),
                    null,
                    "Sanction blocked: requires APPROVED");
            throw new BusinessRuleException(
                    "Cannot sanction — application must be in APPROVED status. Current: " + app.getStatus(),
                    "STATUS_NOT_APPROVED",
                    "SANCTION",
                    Map.of("status", app.getStatus().name())
            );
        }

        // Allow overriding sanctioned amount and rate
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

        // Ensure sanctioned amount and rate are set
        if (app.getSanctionedAmount() == null) {
            app.setSanctionedAmount(app.getRequestedAmount());
        }
        if (app.getApprovedRate() == null && app.getInterestRate() != null) {
            app.setApprovedRate(app.getInterestRate());
        }
        if (app.getInterestRate() == null && app.getApprovedRate() != null) {
            app.setInterestRate(app.getApprovedRate());
        }

        app.setStatus(ApplicationStatus.SANCTION_ISSUED);
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        // Generate KFS (RBI-compliant Key Fact Statement)
        Map<String, Object> charges = sanctionParams != null ? sanctionParams : Map.of();
        KfsDocument kfs = kfsService.generateKfs(applicationId, charges);

        auditService.logEvent(applicationId, "FLOW", "SANCTION_ISSUED",
                null, Map.of("status", "APPROVED"),
                Map.of("status", "SANCTION_ISSUED", "sanctionedAmount", app.getSanctionedAmount().toString(),
                        "kfsId", kfs.getId().toString()),
                "Sanction issued with KFS generated");

        log.info("Sanction issued for {} — amount: {}, rate: {}%, KFS: {}",
                app.getApplicationNumber(), app.getSanctionedAmount(), app.getApprovedRate(), kfs.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", applicationId);
        result.put("applicationNumber", app.getApplicationNumber());
        result.put("status", "SANCTION_ISSUED");
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
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.SANCTION_ISSUED) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "ESIGN_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "STATUS_NOT_SANCTION_ISSUED", "action", "ESIGN_INITIATE"),
                    null,
                    "eSign blocked: requires SANCTION_ISSUED");
            throw new BusinessRuleException(
                    "Cannot initiate eSign — application must be in SANCTION_ISSUED status. Current: " + app.getStatus(),
                    "STATUS_NOT_SANCTION_ISSUED",
                    "ESIGN_INITIATE",
                    Map.of("status", app.getStatus().name())
            );
        }

        // Route eSign request through integration router
        IIntegrationRouterService.ESignRouteResult result =
                integrationRouter.routeESignRequest(applicationId, "KFS_AGREEMENT", signerInfo);

        if (result.success()) {
            app.setStatus(ApplicationStatus.ESIGN_PENDING);
            app.setEsignTransactionId(result.transactionId());
            app.setCurrentStepStartedAt(Instant.now());
            app = applicationRepository.save(app);

            auditService.logEvent(applicationId, "FLOW", "ESIGN_INITIATED",
                    null, Map.of("status", "SANCTION_ISSUED"),
                    Map.of("status", "ESIGN_PENDING", "esignTransactionId", result.transactionId()),
                    "eSign initiated");

            log.info("eSign initiated for {} — txnId: {}, signingUrl: {}",
                    app.getApplicationNumber(), result.transactionId(), result.signingUrl());
        } else {
            log.error("eSign initiation failed for {}: {}", app.getApplicationNumber(), result.errorMessage());
        }

        return Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "esignSuccess", result.success(),
                "transactionId", result.transactionId() != null ? result.transactionId() : "",
                "signingUrl", result.signingUrl() != null ? result.signingUrl() : "",
                "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
        );
    }

    /**
     * Complete eSign (called by webhook or manually after eSign provider confirms signing).
     * Transitions from ESIGN_PENDING to DISBURSEMENT_PENDING.
     */
    @Transactional
    public ApplicationResponse completeESign(UUID applicationId, String esignTransactionId) {
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

        // Transition through ESIGN_COMPLETED (state machine: ESIGN_PENDING → ESIGN_COMPLETED → DISBURSEMENT_PENDING)
        app.setStatus(ApplicationStatus.ESIGN_COMPLETED);
        if (esignTransactionId != null) {
            app.setEsignTransactionId(esignTransactionId);
        }
        // Immediately advance to DISBURSEMENT_PENDING (ESIGN_COMPLETED is a transient acknowledgement state)
        app.setStatus(ApplicationStatus.DISBURSEMENT_PENDING);
        app.setCurrentStepStartedAt(Instant.now());
        app = applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "ESIGN_COMPLETE",
                null, Map.of("status", "ESIGN_PENDING"),
                Map.of("status", "DISBURSEMENT_PENDING", "esignTransactionId",
                        esignTransactionId != null ? esignTransactionId : ""),
                "eSign completed — transitioned through ESIGN_COMPLETED to DISBURSEMENT_PENDING");

        log.info("eSign completed for {} — status: DISBURSEMENT_PENDING", app.getApplicationNumber());
        return toResponse(app);
    }

    // ========================== STEP 7: DISBURSE + LMS ==========================

    /**
     * Process disbursement and hand over to Encore LMS.
     * Transitions from DISBURSEMENT_PENDING to DISBURSED.
     */
    @Transactional
    public Map<String, Object> disburseAndHandoverToLms(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        if (app.getStatus() != ApplicationStatus.DISBURSEMENT_PENDING) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "DISBURSEMENT_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "STATUS_NOT_DISBURSEMENT_PENDING", "action", "DISBURSE"),
                    null,
                    "Disbursement blocked: requires DISBURSEMENT_PENDING");
            throw new BusinessRuleException(
                    "Cannot disburse — application must be in DISBURSEMENT_PENDING status. Current: " + app.getStatus(),
                    "STATUS_NOT_DISBURSEMENT_PENDING",
                    "DISBURSE",
                    Map.of("status", app.getStatus().name())
            );
        }

        BigDecimal disbursementAmount = app.getSanctionedAmount() != null
                ? app.getSanctionedAmount() : app.getRequestedAmount();
        BigDecimal rate = app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();

        // Calculate EMI for LMS handover
        BigDecimal emiAmount = calculateEmi(disbursementAmount, rate, app.getTenureMonths());

        // Extract borrower name from personalInfo
        String borrowerName = "";
        if (app.getPersonalInfo() != null) {
            Object name = app.getPersonalInfo().get("fullName");
            if (name == null) name = app.getPersonalInfo().get("name");
            if (name == null) {
                String first = (String) app.getPersonalInfo().getOrDefault("firstName", "");
                String last = (String) app.getPersonalInfo().getOrDefault("lastName", "");
                name = (first + " " + last).trim();
            }
            borrowerName = name.toString();
        }

        // Call LMS adapter to create loan account in Encore
        Map<String, Object> lmsResult = lmsAdapterClient.handoverLoan(
                applicationId, app.getApplicationNumber(),
                borrowerName, app.getBorrowerType().name(),
                app.getLoanProduct(), disbursementAmount,
                rate, app.getTenureMonths(), emiAmount,
                app.getPersonalInfo()
        );

        String lmsStatus = String.valueOf(lmsResult.getOrDefault("status", "UNKNOWN"));
        String lmsReferenceId = String.valueOf(lmsResult.getOrDefault("lmsReferenceId", ""));

        // Update application regardless of LMS outcome
        app.setStatus(ApplicationStatus.DISBURSED);
        app.setDisbursedAmount(disbursementAmount);
        app.setDisbursedAt(Instant.now());
        if (!lmsReferenceId.isEmpty() && !"null".equals(lmsReferenceId)) {
            app.setLmsReferenceId(lmsReferenceId);
        }
        app = applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "DISBURSED",
                null, Map.of("status", "DISBURSEMENT_PENDING"),
                Map.of("status", "DISBURSED", "disbursedAmount", disbursementAmount.toString(),
                        "lmsReferenceId", lmsReferenceId, "lmsStatus", lmsStatus),
                "Loan disbursed and handed over to LMS");

        log.info("Application {} DISBURSED — amount: {}, LMS ref: {}, LMS status: {}",
                app.getApplicationNumber(), disbursementAmount, lmsReferenceId, lmsStatus);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("applicationId", applicationId);
        response.put("applicationNumber", app.getApplicationNumber());
        response.put("status", "DISBURSED");
        response.put("disbursedAmount", disbursementAmount);
        response.put("interestRate", rate);
        response.put("tenureMonths", app.getTenureMonths());
        response.put("emiAmount", emiAmount);
        response.put("disbursedAt", app.getDisbursedAt().toString());
        response.put("lmsReferenceId", lmsReferenceId);
        response.put("lmsStatus", lmsStatus);
        response.put("lmsDetails", lmsResult);
        return response;
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

    private BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, Integer tenureMonths) {
        if (principal == null || tenureMonths == null || tenureMonths == 0) return BigDecimal.ZERO;
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }
        double p = principal.doubleValue();
        double r = annualRate.doubleValue() / 12.0 / 100.0;
        int n = tenureMonths;
        double emi = p * r * Math.pow(1 + r, n) / (Math.pow(1 + r, n) - 1);
        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
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
                .bureauScore(app.getBureauScore())
                .creditDecision(app.getCreditDecision())
                .creditRiskScore(app.getCreditRiskScore())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .submittedAt(app.getSubmittedAt())
                .build();
    }
}
