package com.los.core.service.vkyc;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.StepExecutionRecord;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.VkycCompletionMode;
import com.los.core.model.enums.VkycPkycReason;
import com.los.core.model.enums.VkycStatus;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.StepExecutionRecordRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.flow.step.StepExecutionRecordWriter;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.core.service.workflow.WorkflowNotificationResolverService;
import com.los.core.config.VkycNotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VkycWorkflowService {

    /** Document type for PKYC evidence uploads ({@code POST /documents/.../upload}). */
    public static final String PHYSICAL_KYC_DOCUMENT_TYPE = "PHYSICAL_KYC_EVIDENCE";

    private static final Set<String> PKYC_ALLOWED_ROLES = Set.of(
            "ADMIN",
            "ADMINISTRATOR",
            "OPERATIONS",
            "VKYC_MANAGER",
            "RISK_MANAGER",
            "CREDIT_MANAGER",
            "BRANCH_VERIFIER",
            "KYC_REVIEWER"
    );
    private static final Set<ApplicationStatus> PKYC_FORBIDDEN_APP_STATUS = Set.of(
            ApplicationStatus.REJECTED,
            ApplicationStatus.WITHDRAWN,
            ApplicationStatus.DISBURSED
    );
    private static final Set<String> PKYC_ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private static final Set<String> VKYC_STEP_NAMES = Set.of("VIDEO_KYC", "VKYC");
    private static final Set<VkycStatus> RESEND_BLOCKED = Set.of(
            VkycStatus.AGENT_APPROVED,
            VkycStatus.AUDITOR_APPROVED,
            VkycStatus.COMPLETED,
            VkycStatus.REJECTED
    );
    private static final Map<VkycStatus, Set<VkycStatus>> ALLOWED = Map.of(
            VkycStatus.NOT_STARTED, Set.of(VkycStatus.URL_GENERATED, VkycStatus.FAILED, VkycStatus.EXPIRED),
            VkycStatus.PENDING, Set.of(VkycStatus.URL_GENERATED, VkycStatus.FAILED, VkycStatus.EXPIRED), // backward-compat alias
            VkycStatus.URL_GENERATED, Set.of(VkycStatus.CUSTOMER_JOINED, VkycStatus.AGENT_APPROVED, VkycStatus.REJECTED, VkycStatus.EXPIRED, VkycStatus.FAILED),
            VkycStatus.CUSTOMER_JOINED, Set.of(VkycStatus.AGENT_APPROVED, VkycStatus.REJECTED, VkycStatus.FAILED),
            VkycStatus.AGENT_APPROVED, Set.of(VkycStatus.AUDITOR_APPROVED, VkycStatus.REJECTED, VkycStatus.FAILED),
            VkycStatus.AUDITOR_APPROVED, Set.of(VkycStatus.COMPLETED, VkycStatus.REJECTED, VkycStatus.FAILED),
            VkycStatus.REJECTED, Set.of(VkycStatus.URL_GENERATED),
            VkycStatus.EXPIRED, Set.of(VkycStatus.URL_GENERATED),
            VkycStatus.FAILED, Set.of(VkycStatus.URL_GENERATED),
            VkycStatus.COMPLETED, Set.of()
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final StepExecutionRecordRepository stepExecutionRecordRepository;
    private final StepExecutionRecordWriter stepExecutionRecordWriter;
    private final AuditService auditService;
    private final IIntegrationRouterService integrationRouterService;
    private final HypervergeVkycClient hypervergeVkycClient;
    private final VkycNotificationProperties vkycNotificationProperties;
    private final VkycLinkNotifier vkycLinkNotifier;
    private final LosUserRepository losUserRepository;
    private final DocumentRepository documentRepository;
    private final WorkflowNotificationResolverService workflowNotificationResolverService;

    public Map<String, Object> getVkycConfiguration(UUID applicationId) {
        LoanApplication app = loadApp(applicationId);
        WorkflowConfig cfg = loadWorkflow(app).orElse(null);
        boolean hasStep = cfg != null && hasVkycStep(cfg.getSteps());
        return Map.of(
                "applicationId", applicationId.toString(),
                "workflowId", cfg != null ? cfg.getId().toString() : "",
                "vkycPresentInWorkflow", hasStep,
                "workflowPosition", cfg != null && cfg.getWorkflowPosition() != null ? cfg.getWorkflowPosition() : "",
                "vkycTriggerCondition", cfg != null && cfg.getVkycTriggerCondition() != null ? cfg.getVkycTriggerCondition() : List.of(),
                "allowPhysicalKycFallback", cfg != null && isPhysicalKycFallbackAllowedOnWorkflow(cfg),
                "steps", cfg != null && cfg.getSteps() != null ? cfg.getSteps() : List.of()
        );
    }

    public Map<String, Object> evaluateEligibility(UUID applicationId) {
        LoanApplication app = loadApp(applicationId);
        WorkflowConfig cfg = loadWorkflow(app).orElse(null);
        Map<String, Object> result = evaluateEligibilityInternal(app, cfg);
        if (result.containsKey("evaluations")) {
            auditService.logEvent(applicationId, "VKYC_CONDITION_EVAL", "VKYC_CONDITION_EVAL", null, null,
                    Map.of("eligible", result.get("eligible"), "evaluations", result.get("evaluations")),
                    "VKYC trigger condition evaluation");
        }
        return result;
    }

    /**
     * Pure rule evaluation — does NOT write audit events. Used by the downstream-action
     * guard to avoid emitting an audit row on every CAM / sanction / eSign / disburse
     * call. The user-facing public {@link #evaluateEligibility(UUID)} keeps writing audit
     * events as before so existing consumers and dashboards are unaffected.
     */
    private Map<String, Object> evaluateEligibilityInternal(LoanApplication app, WorkflowConfig cfg) {
        if (cfg == null || !hasVkycStep(cfg.getSteps())) {
            return Map.of("eligible", false, "reason", "VKYC_STEP_NOT_CONFIGURED");
        }
        List<Map<String, Object>> rules = cfg.getVkycTriggerCondition() != null ? cfg.getVkycTriggerCondition() : List.of();
        if (rules.isEmpty()) {
            return Map.of("eligible", true, "reason", "NO_TRIGGER_CONDITION_CONFIGURED");
        }
        List<Map<String, Object>> eval = new ArrayList<>();
        boolean allTrue = true;
        for (Map<String, Object> r : rules) {
            boolean ok = evaluateRule(app, r);
            allTrue = allTrue && ok;
            eval.add(Map.of("rule", r, "matched", ok));
        }
        return Map.of("eligible", allTrue, "evaluations", eval);
    }

    /**
     * Workflow-governance constants used by {@link #assertVkycCleared(UUID, String)}.
     *
     * <p>The downstream-action codes mirror the loan flow service methods that move the
     * application forward after the VKYC checkpoint. Position codes correspond to the
     * {@code workflowPosition} captured on {@link WorkflowConfig}.</p>
     */
    public static final class DownstreamAction {
        public static final String MARK_CAM_REVIEWED = "MARK_CAM_REVIEWED";
        public static final String PROCEED_TO_SANCTION_PENDING = "PROCEED_TO_SANCTION_PENDING";
        public static final String SANCTION = "SANCTION";
        public static final String INITIATE_ESIGN = "INITIATE_ESIGN";
        public static final String COMPLETE_ESIGN = "COMPLETE_ESIGN";
        public static final String READY_FOR_DISBURSEMENT = "READY_FOR_DISBURSEMENT";
        public static final String DISBURSE = "DISBURSE";
        private DownstreamAction() {}
    }

    private static final Map<String, Integer> ACTION_TIER = Map.of(
            DownstreamAction.MARK_CAM_REVIEWED, 1,
            DownstreamAction.PROCEED_TO_SANCTION_PENDING, 1,
            DownstreamAction.SANCTION, 2,
            DownstreamAction.INITIATE_ESIGN, 3,
            DownstreamAction.COMPLETE_ESIGN, 3,
            DownstreamAction.READY_FOR_DISBURSEMENT, 4,
            DownstreamAction.DISBURSE, 4
    );

    /**
     * Returns the lowest action tier that the configured VKYC position governs. Actions whose
     * tier is greater than or equal to this value must wait until VKYC is auditor-approved.
     * <p>For unrecognised / null positions a conservative default of tier 3 (eSign onwards)
     * is used so disbursement is always governed when VKYC is configured but the position
     * code cannot be matched.</p>
     */
    private static int positionGovernsFromTier(String workflowPosition) {
        String pos = workflowPosition == null ? "" : workflowPosition.trim().toUpperCase(Locale.ROOT);
        return switch (pos) {
            case "AFTER_UNDERWRITING" -> 1;
            case "BEFORE_ESIGN" -> 3;
            case "AFTER_ESIGN" -> 4;
            default -> 3;
        };
    }

    private static final Set<VkycStatus> VKYC_AUDITOR_CLEARED = Set.of(
            VkycStatus.AUDITOR_APPROVED,
            VkycStatus.COMPLETED
    );

    /**
     * Workflow-governance gate: blocks downstream loan-flow actions until VKYC has been
     * auditor-approved, when VKYC is actually applicable to the application's active
     * workflow.
     *
     * <p>This method is intentionally side-effect free (no DB writes, no audit events) so it
     * can be called at the start of every flow-service method without bloating the audit
     * log or affecting transaction semantics. It is the backend-side enforcement
     * counterpart to the UI gate driven by {@code vkycWorkflowGate.ts} on the frontend.</p>
     *
     * <p>The guard returns silently when:
     * <ul>
     *   <li>The application's active workflow does not include a VKYC step.</li>
     *   <li>The configured VKYC trigger conditions evaluate to {@code eligible == false}
     *       for this application (i.e. VKYC is not required for this loan).</li>
     *   <li>The action's tier is below the configured VKYC position tier (the action
     *       happens before VKYC in the workflow).</li>
     *   <li>VKYC has already been cleared — status is {@code AUDITOR_APPROVED} or
     *       {@code COMPLETED}.</li>
     * </ul>
     * Otherwise it raises a {@link BusinessRuleException} with code
     * {@code VKYC_AUDITOR_APPROVAL_PENDING}.</p>
     *
     * <p>URL generation, customer-joined and agent-approved states do <strong>not</strong>
     * count as cleared — only the auditor approval (or its terminal {@code COMPLETED}
     * follow-up) unblocks downstream actions.</p>
     */
    public void assertVkycCleared(UUID applicationId, String downstreamAction) {
        if (applicationId == null || downstreamAction == null) {
            return;
        }
        Integer actionTier = ACTION_TIER.get(downstreamAction);
        if (actionTier == null) {
            return;
        }
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null) {
            return;
        }
        WorkflowConfig cfg = loadWorkflow(app).orElse(null);
        if (cfg == null || !hasVkycStep(cfg.getSteps())) {
            return;
        }
        Map<String, Object> eligibility = evaluateEligibilityInternal(app, cfg);
        if (!Boolean.TRUE.equals(eligibility.get("eligible"))) {
            return;
        }
        int positionTier = positionGovernsFromTier(cfg.getWorkflowPosition());
        if (actionTier < positionTier) {
            return;
        }
        VkycStatus status = app.getVkycStatus();
        if (status != null && VKYC_AUDITOR_CLEARED.contains(status)) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action", downstreamAction);
        details.put("workflowPosition", cfg.getWorkflowPosition() != null ? cfg.getWorkflowPosition() : "");
        details.put("vkycStatus", status != null ? status.name() : VkycStatus.NOT_STARTED.name());
        details.put("requiredVkycStatus", VkycStatus.AUDITOR_APPROVED.name() + " | " + VkycStatus.COMPLETED.name());
        throw new BusinessRuleException(
                "Action blocked — VKYC auditor approval is pending for this application",
                "VKYC_AUDITOR_APPROVAL_PENDING",
                downstreamAction,
                details
        );
    }

    @Transactional
    public Map<String, Object> generateVkycUrl(UUID applicationId, UUID performedBy) {
        LoanApplication app = loadApp(applicationId);
        if (isVkycSatisfiedByPkyc(app)) {
            log.info("Skipping HyperVerge VKYC link generation because PKYC completion detected for applicationId={}", applicationId);
            auditService.logEvent(applicationId, "VKYC", "HYPERVERGE_BYPASSED_DUE_TO_PKYC", performedBy, null,
                    Map.of("action", "VKYC_GENERATE_URL", "vkycCompletionMode", VkycCompletionMode.PKYC.name()),
                    "HyperVerge VKYC link generation skipped — application completed through Physical KYC");
            throw new BusinessRuleException(
                    "VKYC link generation is not available — this application was completed via Physical KYC (PKYC).",
                    "VKYC_BLOCKED_BY_PKYC",
                    "VKYC_GENERATE_URL",
                    Map.of("vkycCompletionMode", VkycCompletionMode.PKYC.name()));
        }
        Map<String, Object> eligibility = evaluateEligibility(applicationId);
        if (!Boolean.TRUE.equals(eligibility.get("eligible"))) {
            throw new BusinessRuleException("VKYC not eligible for this application", "VKYC_NOT_ELIGIBLE", "VKYC_GENERATE_URL", eligibility);
        }
        if (app.getVkycStatus() == null || app.getVkycStatus() == VkycStatus.PENDING) {
            app.setVkycStatus(VkycStatus.NOT_STARTED);
        }
        assertTransition(app.getVkycStatus(), VkycStatus.URL_GENERATED);
        if (app.getVkycUrl() != null && !app.getVkycUrl().isBlank() && !isExpired(app)) {
            return Map.of(
                    "referenceId", nvl(app.getVkycReferenceId()),
                    "url", app.getVkycUrl(),
                    "status", VkycStatus.URL_GENERATED.name(),
                    "reused", true
            );
        }
        Map<String, Object> providerPayload = buildVkycProviderPayload(app);
        String transactionId = "HV-" + UUID.randomUUID();
        Map<String, Object> parsed = hypervergeVkycClient.generateLink(applicationId, transactionId, providerPayload);
        Map<String, Object> result = parsed.get("result") instanceof Map<?, ?> m ? (Map<String, Object>) m : parsed;
        String url = String.valueOf(result.getOrDefault("startKycUrl", result.getOrDefault("vkycUrl", "")));
        String ref = String.valueOf(result.getOrDefault("workflowId", ""));
        String txn = String.valueOf(result.getOrDefault("transactionId", transactionId));
        if (url.isBlank()) {
            throw new BusinessRuleException("VKYC provider did not return URL", "VKYC_URL_MISSING", "VKYC_GENERATE_URL", parsed);
        }
        Instant generatedAt = Instant.now();
        Instant expiryAt = generatedAt.plus(vkycNotificationProperties.getLinkExpiryHours(), ChronoUnit.HOURS);
        app.setVkycRequired(true);
        app.setVkycReferenceId(!ref.isBlank() ? ref : (!txn.isBlank() ? txn : "VKYC-" + applicationId.toString().substring(0, 8)));
        app.setVkycUrl(url);
        app.setVkycTransactionId(txn);
        app.setVkycUrlGeneratedAt(generatedAt);
        app.setVkycUrlExpiryAt(expiryAt);
        app.setVkycGeneratedBy(performedBy);
        app.setVkycResendCount(app.getVkycResendCount() != null ? app.getVkycResendCount() : 0);
        app.setVkycStatus(VkycStatus.URL_GENERATED);
        loanApplicationRepository.save(app);
        sendVkycEmail(app, false);
        app.setVkycLastEvent("URL_GENERATED");
        app.setVkycEventPayload(parsed.toString());
        loanApplicationRepository.save(app);
        recordTimeline(applicationId, "VKYC_URL_GENERATED", Map.of("referenceId", app.getVkycReferenceId(), "url", url, "expiryAt", expiryAt.toString(), "transactionId", txn));
        auditService.logEvent(applicationId, "VKYC", "URL_GENERATED", performedBy, null,
                Map.of("vkycStatus", VkycStatus.URL_GENERATED.name(), "vkycReferenceId", app.getVkycReferenceId(), "vkycUrl", url, "expiryAt", expiryAt.toString()),
                "VKYC URL generated from VKYC tab");
        return Map.of("referenceId", app.getVkycReferenceId(), "url", url, "status", VkycStatus.URL_GENERATED.name(), "generatedAt", generatedAt.toString(), "expiryAt", expiryAt.toString());
    }

    @Transactional
    public Map<String, Object> processWebhookEvent(Map<String, Object> payload) {
        String txn = String.valueOf(payload.getOrDefault("transactionId", ""));
        if (txn.isBlank()) {
            throw new BusinessRuleException("transactionId missing", "VKYC_TXN_REQUIRED", "VKYC_WEBHOOK", null);
        }
        LoanApplication app = loanApplicationRepository.findTopByVkycTransactionId(txn)
                .orElseThrow(() -> new ResourceNotFoundException("No application for vkyc transactionId: " + txn));
        if (isVkycSatisfiedByPkyc(app)) {
            log.info("VKYC webhook ignored — PKYC completion already recorded for applicationId={}", app.getId());
            auditService.logEvent(app.getId(), "VKYC", "WEBHOOK_IGNORED_PKYC_COMPLETED", null, null,
                    Map.of("transactionId", txn, "vkycCompletionMode", VkycCompletionMode.PKYC.name()),
                    "HyperVerge VKYC webhook ignored — Physical KYC already satisfied this checkpoint");
            return Map.of(
                    "applicationId", app.getId().toString(),
                    "transactionId", txn,
                    "ignored", true,
                    "reason", "PKYC_COMPLETED");
        }
        String hvStatus = String.valueOf(payload.getOrDefault("status", "")).toLowerCase(Locale.ROOT);
        VkycStatus mapped = mapWebhookStatus(hvStatus);
        if (mapped != null) {
            app.setVkycStatus(mapped);
            if (mapped == VkycStatus.COMPLETED) {
                app.setVkycCompletedOn(Instant.now());
                app.setVkycCompletedAt(Instant.now());
            }
        }
        app.setVkycLastEvent(hvStatus.toUpperCase(Locale.ROOT));
        app.setVkycEventPayload(payload.toString());
        Map<String, Object> resultPayload = hypervergeVkycClient.fetchResult(app.getId(), txn);
        app.setVkycResultPayload(resultPayload.toString());
        Object resultObj = resultPayload.get("result");
        if (resultObj instanceof Map<?, ?> r) {
            Object agent = r.get("agentName");
            if (agent != null) app.setVkycAgentName(String.valueOf(agent));
            app.setVkycAgentUpdatedOn(Instant.now());
            Object aml = r.get("amlHit");
            if (aml != null) app.setAmlHit(Boolean.parseBoolean(String.valueOf(aml)));
            if (r.get("videoUrl") != null) app.setVkycVideoUrl(String.valueOf(r.get("videoUrl")));
            if (r.get("panImageUrl") != null) app.setVkycPanImageUrl(String.valueOf(r.get("panImageUrl")));
            if (r.get("faceImageUrl") != null) app.setVkycFaceImageUrl(String.valueOf(r.get("faceImageUrl")));
        }
        loanApplicationRepository.save(app);
        auditService.logEvent(app.getId(), "VKYC", "WEBHOOK_EVENT", null, null,
                Map.of("transactionId", txn, "status", hvStatus, "mappedStatus", mapped != null ? mapped.name() : ""),
                "HyperVerge VKYC webhook processed");
        return Map.of("applicationId", app.getId().toString(), "transactionId", txn, "mappedStatus", mapped != null ? mapped.name() : "UNMAPPED");
    }

    private VkycStatus mapWebhookStatus(String status) {
        return switch (status) {
            case "needs_review" -> VkycStatus.AGENT_APPROVED;
            case "auto_declined" -> VkycStatus.AUTO_DECLINED;
            case "manually_approved" -> VkycStatus.AUDITOR_APPROVED;
            case "manually_declined" -> VkycStatus.AUDITOR_REJECTED;
            case "error" -> VkycStatus.ERROR;
            case "initiated" -> VkycStatus.INITIATED;
            case "completed" -> VkycStatus.COMPLETED;
            default -> null;
        };
    }

    @Transactional
    public Map<String, Object> resendVkycUrl(UUID applicationId, UUID actorUserId) {
        LoanApplication app = loadApp(applicationId);
        if (isVkycSatisfiedByPkyc(app)) {
            log.info("VKYC notification skipped because PKYC completion detected for applicationId={}", applicationId);
            auditService.logEvent(applicationId, "VKYC", "NOTIFICATION_SKIPPED_DUE_TO_PKYC", actorUserId, null,
                    Map.of("action", "VKYC_RESEND", "event", "VKYC_LINK"),
                    "VKYC link resend skipped — application completed through Physical KYC");
            throw new BusinessRuleException(
                    "VKYC link resend is not available — this application was completed via Physical KYC (PKYC).",
                    "VKYC_RESEND_BLOCKED_BY_PKYC",
                    "VKYC_RESEND",
                    Map.of("vkycCompletionMode", VkycCompletionMode.PKYC.name()));
        }
        VkycStatus status = app.getVkycStatus() != null ? app.getVkycStatus() : VkycStatus.NOT_STARTED;
        if (RESEND_BLOCKED.contains(status)) {
            throw new BusinessRuleException("VKYC resend is not allowed at current stage", "VKYC_RESEND_BLOCKED", "VKYC_RESEND", Map.of("status", status.name()));
        }
        boolean generatedNew = false;
        if (app.getVkycUrl() == null || app.getVkycUrl().isBlank() || isExpired(app)) {
            generateVkycUrl(applicationId, actorUserId);
            app = loadApp(applicationId);
            generatedNew = true;
        } else {
            sendVkycEmail(app, true);
        }
        app.setVkycLastResentAt(Instant.now());
        app.setVkycResendCount((app.getVkycResendCount() != null ? app.getVkycResendCount() : 0) + 1);
        loanApplicationRepository.save(app);
        recordTimeline(applicationId, "VKYC_RESEND", Map.of("resentBy", actorUserId != null ? actorUserId.toString() : "", "generatedNew", generatedNew ? "true" : "false"));
        auditService.logEvent(applicationId, "VKYC", "RESEND_LINK", actorUserId, null,
                Map.of("vkycResendCount", String.valueOf(app.getVkycResendCount()), "generatedNew", generatedNew, "vkycStatus", nvl(status.name())),
                "VKYC link resent");
        return Map.of("success", true, "generatedNew", generatedNew, "resendCount", app.getVkycResendCount(), "url", nvl(app.getVkycUrl()), "expiryAt", app.getVkycUrlExpiryAt() != null ? app.getVkycUrlExpiryAt().toString() : "");
    }

    @Transactional
    public Map<String, Object> updateVkycStage(UUID applicationId, VkycStatus target, UUID actorUserId) {
        LoanApplication app = loadApp(applicationId);
        VkycStatus from = app.getVkycStatus() != null ? app.getVkycStatus() : VkycStatus.NOT_STARTED;
        assertTransition(from, target);
        app.setVkycStatus(target);
        if (target == VkycStatus.AGENT_APPROVED) app.setVkycAgentId(actorUserId);
        if (target == VkycStatus.AUDITOR_APPROVED) app.setVkycAuditorId(actorUserId);
        if (target == VkycStatus.COMPLETED) app.setVkycCompletedAt(Instant.now());
        loanApplicationRepository.save(app);
        recordTimeline(applicationId, "VKYC_" + target.name(), Map.of("from", from.name(), "to", target.name()));
        auditService.logEvent(applicationId, "VKYC", target.name(), actorUserId,
                Map.of("vkycStatus", from.name()),
                Map.of("vkycStatus", target.name()),
                "VKYC stage transition");
        return Map.of("status", target.name(), "from", from.name(), "to", target.name());
    }

    public Map<String, Object> getTimeline(UUID applicationId) {
        LoanApplication app = loadApp(applicationId);
        WorkflowConfig cfg = loadWorkflow(app).orElse(null);
        List<StepExecutionRecord> records = stepExecutionRecordRepository.findByApplicationIdAndStepTypeStartingWithOrderByStartedAtAsc(applicationId, "VKYC_");
        List<Map<String, Object>> stages = new ArrayList<>();
        for (StepExecutionRecord r : records) {
            stages.add(Map.of(
                    "stage", r.getStepType(),
                    "status", r.getStatus().name(),
                    "timestamp", r.getCompletedAt() != null ? r.getCompletedAt().toString() : r.getStartedAt().toString()
            ));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        out.put("allowPhysicalKycFallback", cfg != null && isPhysicalKycFallbackAllowedOnWorkflow(cfg));
        out.put("vkycRequired", Boolean.TRUE.equals(app.getVkycRequired()));
        out.put("vkycStatus", app.getVkycStatus() != null ? app.getVkycStatus().name() : VkycStatus.NOT_STARTED.name());
        out.put("vkycReferenceId", app.getVkycReferenceId() != null ? app.getVkycReferenceId() : "");
        out.put("vkycUrl", app.getVkycUrl() != null ? app.getVkycUrl() : "");
        out.put("vkycUrlGeneratedAt", app.getVkycUrlGeneratedAt() != null ? app.getVkycUrlGeneratedAt().toString() : "");
        out.put("vkycUrlExpiryAt", app.getVkycUrlExpiryAt() != null ? app.getVkycUrlExpiryAt().toString() : "");
        out.put("vkycLastResentAt", app.getVkycLastResentAt() != null ? app.getVkycLastResentAt().toString() : "");
        out.put("vkycResendCount", app.getVkycResendCount() != null ? app.getVkycResendCount() : 0);
        out.put("vkycEmailSent", Boolean.TRUE.equals(app.getVkycEmailSent()));
        out.put("vkycEmailSentAt", app.getVkycEmailSentAt() != null ? app.getVkycEmailSentAt().toString() : "");
        out.put("vkycGeneratedBy", app.getVkycGeneratedBy() != null ? app.getVkycGeneratedBy().toString() : "");
        out.put("vkycAgentId", app.getVkycAgentId() != null ? app.getVkycAgentId().toString() : "");
        out.put("vkycAuditorId", app.getVkycAuditorId() != null ? app.getVkycAuditorId().toString() : "");
        out.put("vkycCompletedAt", app.getVkycCompletedAt() != null ? app.getVkycCompletedAt().toString() : "");
        out.put("vkycCompletionMode", app.getVkycCompletionMode() != null ? app.getVkycCompletionMode().name() : "");
        out.put("pkycReason", app.getPkycReason() != null ? app.getPkycReason() : "");
        out.put("pkycComments", app.getPkycComments() != null ? app.getPkycComments() : "");
        out.put("pkycDocumentId", app.getPkycDocumentId() != null ? app.getPkycDocumentId().toString() : "");
        out.put("pkycVerifiedBy", app.getPkycVerifiedBy() != null ? app.getPkycVerifiedBy().toString() : "");
        out.put("pkycVerifiedAt", app.getPkycVerifiedAt() != null ? app.getPkycVerifiedAt().toString() : "");
        out.put("stages", stages);
        return out;
    }

    /**
     * Completes the VKYC checkpoint using physical/offline verification (PKYC fallback).
     * Leaves normal HyperVerge / stage-transition flows untouched.
     */
    @Transactional
    public Map<String, Object> completePhysicalKyc(
            UUID applicationId,
            VkycPkycReason reason,
            String comments,
            UUID documentId,
            UUID actorUserId,
            Set<String> userRoles) {
        assertPkycRoles(userRoles);
        if (actorUserId == null) {
            throw new BusinessRuleException("Physical KYC completion requires authenticated user id", "PKYC_USER_REQUIRED", "PKYC_COMPLETE", null);
        }
        if (reason == null) {
            throw new BusinessRuleException("PKYC reason is required", "PKYC_REASON_REQUIRED", "PKYC_COMPLETE", null);
        }
        if (comments == null || comments.isBlank()) {
            throw new BusinessRuleException("PKYC comments are required", "PKYC_COMMENTS_REQUIRED", "PKYC_COMPLETE", null);
        }
        if (documentId == null) {
            throw new BusinessRuleException("PKYC document is required", "PKYC_DOCUMENT_REQUIRED", "PKYC_COMPLETE", null);
        }
        String trimmedComments = comments.trim();
        if (trimmedComments.length() > 4000) {
            throw new BusinessRuleException("PKYC comments exceed maximum length", "PKYC_COMMENTS_TOO_LONG", "PKYC_COMPLETE", null);
        }

        LoanApplication app = loadApp(applicationId);
        if (PKYC_FORBIDDEN_APP_STATUS.contains(app.getStatus())) {
            throw new BusinessRuleException("Physical KYC is not allowed for this application status", "PKYC_APP_STATUS_BLOCKED", "PKYC_COMPLETE",
                    Map.of("status", app.getStatus().name()));
        }

        VkycStatus cur = app.getVkycStatus() != null ? app.getVkycStatus() : VkycStatus.NOT_STARTED;
        if (VKYC_AUDITOR_CLEARED.contains(cur) || app.getVkycCompletionMode() == VkycCompletionMode.PKYC) {
            throw new BusinessRuleException("VKYC is already cleared for this application", "PKYC_ALREADY_CLEARED", "PKYC_COMPLETE",
                    Map.of("vkycStatus", cur.name()));
        }

        WorkflowConfig cfg = loadWorkflow(app).orElse(null);
        if (cfg == null || !hasVkycStep(cfg.getSteps())) {
            throw new BusinessRuleException("VKYC is not configured for this workflow", "PKYC_VKYC_NOT_CONFIGURED", "PKYC_COMPLETE", null);
        }
        if (!isPhysicalKycFallbackAllowedOnWorkflow(cfg)) {
            throw new BusinessRuleException("Physical KYC fallback is not enabled for this workflow", "PKYC_FALLBACK_DISABLED", "PKYC_COMPLETE", null);
        }
        Map<String, Object> eligibility = evaluateEligibilityInternal(app, cfg);
        if (!Boolean.TRUE.equals(eligibility.get("eligible"))) {
            throw new BusinessRuleException("VKYC does not apply to this application — PKYC unavailable", "PKYC_NOT_ELIGIBLE", "PKYC_COMPLETE", eligibility);
        }

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        if (!applicationId.equals(doc.getApplicationId())) {
            throw new BusinessRuleException("Document does not belong to this application", "PKYC_DOCUMENT_OWNER", "PKYC_COMPLETE", null);
        }
        if (!PHYSICAL_KYC_DOCUMENT_TYPE.equals(doc.getDocumentType())) {
            throw new BusinessRuleException("PKYC requires document type " + PHYSICAL_KYC_DOCUMENT_TYPE, "PKYC_DOCUMENT_TYPE", "PKYC_COMPLETE",
                    Map.of("actualType", doc.getDocumentType() != null ? doc.getDocumentType() : ""));
        }
        if (!isAllowedPkycContentType(doc.getContentType())) {
            throw new BusinessRuleException("PKYC document must be PDF, JPG, or PNG", "PKYC_DOCUMENT_FORMAT", "PKYC_COMPLETE",
                    Map.of("contentType", doc.getContentType() != null ? doc.getContentType() : ""));
        }

        Instant now = Instant.now();

        auditService.logEvent(applicationId, "VKYC", "PKYC_INITIATED", actorUserId, null,
                Map.of(
                        "user", actorUserId.toString(),
                        "timestamp", now.toString(),
                        "reason", reason.name(),
                        "comments", trimmedComments
                ),
                "VKYC — Physical KYC initiation");

        auditService.logEvent(applicationId, "VKYC", "PKYC_DOCUMENT_UPLOADED", actorUserId, null,
                Map.of("documentId", documentId.toString(), "contentType", doc.getContentType() != null ? doc.getContentType() : "",
                        "fileName", doc.getFileName() != null ? doc.getFileName() : ""),
                "VKYC — PKYC supporting document referenced");

        app.setVkycRequired(true);
        app.setVkycStatus(VkycStatus.AUDITOR_APPROVED);
        app.setVkycAuditorId(actorUserId);
        app.setVkycCompletedAt(now);
        app.setVkycCompletionMode(VkycCompletionMode.PKYC);
        app.setPkycReason(reason.name());
        app.setPkycComments(trimmedComments);
        app.setPkycDocumentId(documentId);
        app.setPkycVerifiedBy(actorUserId);
        app.setPkycVerifiedAt(now);
        app.setVkycLastEvent("PKYC_COMPLETED");
        loanApplicationRepository.save(app);

        Map<String, Object> timelinePayload = Map.of(
                "reason", reason.name(),
                "comments", trimmedComments,
                "verifiedByUserId", actorUserId.toString(),
                "documentId", documentId.toString(),
                "message", "VKYC fallback used — completed through Physical KYC"
        );
        recordTimeline(applicationId, "VKYC_PKYC_COMPLETED", timelinePayload);

        auditService.logEvent(applicationId, "VKYC", "PKYC_COMPLETED", actorUserId,
                Map.of("vkycStatus", cur.name()),
                Map.of(
                        "vkycStatus", VkycStatus.AUDITOR_APPROVED.name(),
                        "vkycCompletionMode", VkycCompletionMode.PKYC.name(),
                        "pkycReason", reason.name(),
                        "pkycVerifiedBy", actorUserId.toString(),
                        "pkycVerifiedAt", now.toString()
                ),
                "VKYC — Physical KYC completed");

        publishPkycFollowUpNotifications(app, reason, trimmedComments);

        return Map.of(
                "status", VkycStatus.AUDITOR_APPROVED.name(),
                "vkycCompletionMode", VkycCompletionMode.PKYC.name(),
                "pkycVerifiedAt", now.toString()
        );
    }

    private void publishPkycFollowUpNotifications(LoanApplication app, VkycPkycReason reason, String comments) {
        String email = resolveContactEmail(app);
        List<String> to = (email != null && !email.isBlank()) ? List.of(email.trim()) : List.of();

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("borrowerName", com.los.core.service.loan.ApplicationPartyResolver.resolveDisplayName(app));
        base.put("applicationNumber", app.getApplicationNumber() != null ? app.getApplicationNumber() : "");
        base.put("vkycLink", app.getVkycUrl() != null ? app.getVkycUrl() : "");
        base.put("lenderName", "BillionTech LOS");
        base.put("vkycCompletionMode", VkycCompletionMode.PKYC.name());
        base.put("pkycReason", reason.name());
        base.put("pkycComments", comments);

        // Do not publish VKYC_APPROVED / VKYC_LINK here — those resolve to standard VKYC templates (often
        // multi-channel) and must not fire after PKYC. Optional supplemental: VKYC_COMPLETED_VIA_PKY only.
        Map<String, Object> pkycEvt = new LinkedHashMap<>(base);
        pkycEvt.put("eventType", "VKYC_COMPLETED_VIA_PKY");
        var extra = workflowNotificationResolverService.resolveExplicitOnlyForEvent(
                app.getId(),
                "VIDEO_KYC",
                "VKYC_COMPLETED_VIA_PKY",
                to,
                "VKYC_COMPLETED_VIA_PKY",
                "EMAIL");
        vkycLinkNotifier.publishWorkflowEmailActions(app.getId(), extra, pkycEvt);
    }

    private static void assertPkycRoles(Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            throw new BusinessRuleException("Physical KYC requires an authorized ops role", "PKYC_ROLE_REQUIRED", "PKYC_COMPLETE", null);
        }
        Set<String> normalized = new HashSet<>();
        for (String r : userRoles) {
            if (r != null && !r.isBlank()) {
                normalized.add(r.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (normalized.stream().noneMatch(PKYC_ALLOWED_ROLES::contains)) {
            throw new BusinessRuleException("You are not allowed to complete Physical KYC for this workflow", "PKYC_FORBIDDEN", "PKYC_COMPLETE",
                    Map.of("allowedRoles", PKYC_ALLOWED_ROLES.stream().sorted().toList()));
        }
    }

    private static boolean isPhysicalKycFallbackAllowedOnWorkflow(WorkflowConfig cfg) {
        if (cfg == null || cfg.getSteps() == null) {
            return false;
        }
        for (Map<String, Object> step : cfg.getSteps()) {
            Object raw = step.get("step");
            if (raw == null || !VKYC_STEP_NAMES.contains(String.valueOf(raw).toUpperCase(Locale.ROOT))) {
                continue;
            }
            Object flag = step.get("allowPhysicalKycFallback");
            return Boolean.TRUE.equals(flag)
                    || "true".equalsIgnoreCase(String.valueOf(flag).trim());
        }
        return false;
    }

    private static boolean isAllowedPkycContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String c = raw.toLowerCase(Locale.ROOT).split(";")[0].trim();
        return PKYC_ALLOWED_CONTENT_TYPES.contains(c);
    }

    public Map<String, Object> getWorkflowOrdering(UUID applicationId) {
        LoanApplication app = loadApp(applicationId);
        WorkflowConfig cfg = loadWorkflow(app).orElseThrow(() -> new ResourceNotFoundException("No active workflow"));
        List<Map<String, Object>> sorted = new ArrayList<>(cfg.getSteps() != null ? cfg.getSteps() : List.of());
        sorted.sort(Comparator.comparingInt(this::orderOf));
        return Map.of("workflowId", cfg.getId().toString(), "workflowPosition", cfg.getWorkflowPosition() != null ? cfg.getWorkflowPosition() : "", "orderedSteps", sorted);
    }

    private void assertTransition(VkycStatus from, VkycStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessRuleException("Invalid VKYC transition: " + from + " -> " + to, "INVALID_VKYC_STATE_TRANSITION", "VKYC_STAGE_UPDATE", Map.of("from", from.name(), "to", to.name()));
        }
    }

    private void recordTimeline(UUID applicationId, String stepType, Map<String, Object> output) {
        StepExecutionRecord started = stepExecutionRecordWriter.createStarted(applicationId, stepType, "{}");
        stepExecutionRecordWriter.markSuccess(started.getId(), output.toString());
    }

    private void sendVkycEmail(LoanApplication app, boolean resent) {
        if (isVkycSatisfiedByPkyc(app)) {
            log.info("VKYC notification skipped because PKYC completion detected for applicationId={}", app.getId());
            auditService.logEvent(app.getId(), "VKYC", "NOTIFICATION_SKIPPED_DUE_TO_PKYC", null, null,
                    Map.of("resent", resent ? "true" : "false", "event", "VKYC_LINK"),
                    "VKYC link notification skipped — application completed through Physical KYC");
            return;
        }
        if (!vkycNotificationProperties.isEnabled()) {
            return;
        }
        String email = resolveContactEmail(app);
        if (email == null || email.isBlank()) {
            return;
        }
        String borrowerName = com.los.core.service.loan.ApplicationPartyResolver.resolveDisplayName(app);
        try {
            vkycLinkNotifier.publishVkycLinkEmail(
                    app.getId(),
                    app.getApplicationNumber(),
                    borrowerName,
                    List.of(email),
                    app.getVkycUrl(),
                    vkycNotificationProperties.getTemplateCode(),
                    app.getVkycUrlExpiryAt(),
                    resent
            );
            app.setVkycEmailSent(true);
            app.setVkycEmailSentAt(Instant.now());
            loanApplicationRepository.save(app);
            auditService.logEvent(app.getId(), "VKYC", resent ? "EMAIL_RESEND" : "EMAIL_SENT", null, null,
                    Map.of("recipient", email, "resent", resent ? "true" : "false"),
                    resent ? "VKYC email resent to borrower" : "VKYC email sent to borrower");
        } catch (Exception ex) {
            auditService.logEvent(app.getId(), "VKYC", "EMAIL_SEND_FAILED", null, null,
                    Map.of("recipient", email, "error", ex.getMessage() != null ? ex.getMessage() : "UNKNOWN"),
                    "VKYC email dispatch failed");
        }
    }

    /** Application party email, with registered-user fallback for legacy borrower records. */
    private String resolveContactEmail(LoanApplication app) {
        String fromParty = com.los.core.service.loan.ApplicationPartyResolver.resolveEmail(app);
        if (fromParty != null && !fromParty.isBlank()) {
            return fromParty.trim();
        }
        if (app.getCustomerId() == null) {
            return null;
        }
        return losUserRepository.findById(app.getCustomerId())
                .map(u -> u.getEmail() != null ? u.getEmail().trim() : null)
                .filter(e -> e != null && !e.isBlank())
                .orElse(null);
    }

    private boolean isExpired(LoanApplication app) {
        return app.getVkycUrlExpiryAt() != null && Instant.now().isAfter(app.getVkycUrlExpiryAt());
    }

    /** True when the VKYC checkpoint was satisfied via Physical KYC — no further video/VKYC comms or HV calls. */
    private static boolean isVkycSatisfiedByPkyc(LoanApplication app) {
        return app != null && app.getVkycCompletionMode() == VkycCompletionMode.PKYC;
    }

    private static Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Returns the first value that is non-null AND not blank after trimming.
     * Needed because intake / KYC modules may persist optional keys with empty
     * strings, in which case {@link #firstNonNull(Object...)} would short-circuit
     * with an empty value and skip the real fallback (e.g. {@code dateOfBirth}
     * vs. legacy {@code dob}).
     */
    private static Object firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            if (!String.valueOf(v).trim().isEmpty()) return v;
        }
        return null;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private Map<String, Object> buildVkycProviderPayload(LoanApplication app) {
        return com.los.core.service.loan.ApplicationPartyResolver.buildVkycProviderPayload(app);
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) return;
        String s = String.valueOf(value).trim();
        if (!s.isBlank()) map.put(key, s);
    }

    private Optional<WorkflowConfig> loadWorkflow(LoanApplication app) {
        return activeWorkflowConfigService.findActiveForApplication(app);
    }

    private LoanApplication loadApp(UUID applicationId) {
        return loanApplicationRepository.findById(applicationId).orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private boolean hasVkycStep(List<Map<String, Object>> steps) {
        if (steps == null) return false;
        for (Map<String, Object> s : steps) {
            Object raw = s.get("step");
            if (raw != null && VKYC_STEP_NAMES.contains(String.valueOf(raw).toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private int orderOf(Map<String, Object> step) {
        Object o = step.get("order");
        if (o instanceof Number n) return n.intValue();
        try {
            return o == null ? 0 : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateRule(LoanApplication app, Map<String, Object> rule) {
        String field = String.valueOf(rule.getOrDefault("field", ""));
        String op = String.valueOf(rule.getOrDefault("operator", "==")).toUpperCase(Locale.ROOT);
        Object value = rule.get("value");
        Object left = extractField(app, field);
        if ("IN".equals(op) || "NOT_IN".equals(op)) {
            List<Object> vals = value instanceof List<?> l ? (List<Object>) l : List.of(value);
            boolean contains = vals.stream().map(String::valueOf).anyMatch(v -> v.equalsIgnoreCase(String.valueOf(left)));
            return "IN".equals(op) ? contains : !contains;
        }
        int cmp = compare(left, value);
        return switch (op) {
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case "!=" -> cmp != 0;
            case "==" -> cmp == 0;
            default -> false;
        };
    }

    private int compare(Object left, Object right) {
        try {
            double l = Double.parseDouble(String.valueOf(left));
            double r = Double.parseDouble(String.valueOf(right));
            return Double.compare(l, r);
        } catch (Exception ignored) {
            return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
        }
    }

    private Object extractField(LoanApplication app, String field) {
        String f = field == null ? "" : field.trim().toLowerCase(Locale.ROOT);
        return switch (f) {
            case "loan_amount", "requested_amount" -> app.getRequestedAmount() != null ? app.getRequestedAmount() : 0;
            case "borrower_type" -> app.getBorrowerType() != null ? app.getBorrowerType().name() : "";
            case "product_type", "loan_product" -> app.getLoanProduct() != null ? app.getLoanProduct() : "";
            case "bureau_score" -> app.getBureauScore() != null ? app.getBureauScore() : 0;
            case "risk_grade" -> readMapValue(app.getFinancialInfo(), "riskGrade");
            default -> readMapValue(app.getFinancialInfo(), field);
        };
    }

    private Object readMapValue(Map<String, Object> map, String key) {
        if (map == null || key == null) return "";
        return map.getOrDefault(key, map.getOrDefault(key.toLowerCase(Locale.ROOT), ""));
    }
}
