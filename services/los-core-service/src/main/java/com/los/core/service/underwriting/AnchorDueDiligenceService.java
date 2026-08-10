package com.los.core.service.underwriting;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.plp.service.PlpAnchorSanctionHookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anchor (invoice discounting) underwriting via due-diligence checklist and derived anchor rating.
 * Replaces bureau/scorecard underwriting for anchor onboarding.
 */
@Service
@RequiredArgsConstructor
public class AnchorDueDiligenceService {

    private final LoanApplicationRepository applicationRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final AuditService auditService;
    private final PlpAnchorSanctionHookService plpAnchorSanctionHookService;
    private final AnchorRatingPolicyEngine ratingPolicyEngine;

    @Transactional(readOnly = true)
    public Map<String, Object> getDueDiligence(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        Map<String, Object> result = new LinkedHashMap<>(readDueDiligenceBlock(app));
        result.put("questions", ratingPolicyEngine.questionsForUi());
        result.put("ratingBands", ratingPolicyEngine.ratingBandsForUi());
        return result;
    }

    @Transactional
    public Map<String, Object> saveDueDiligence(UUID applicationId, Map<String, Object> body) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        assertKycReady(app, applicationId);

        Map<String, Object> answers = normalizeAnswers(extractAnswers(body));
        Map<String, Object> comments = normalizeComments(extractComments(body));
        Map<String, Object> block = ratingPolicyEngine.computeBlock(answers, comments, false);
        findActiveTemplateMeta(block);
        block.put("updatedAt", Instant.now().toString());
        mergeDueDiligence(app, block);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "ANCHOR_DD_SAVED", null, null,
                Map.of("creditRating", String.valueOf(block.get("creditRating")),
                        "score", String.valueOf(block.get("score"))),
                "Anchor due diligence checklist saved");
        return enrichForUi(block);
    }

    @Transactional
    public Map<String, Object> completeDueDiligenceUnderwriting(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        assertKycReady(app, applicationId);

        Map<String, Object> block = readDueDiligenceBlock(app);
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) block.get("answers");
        if (answers == null || answers.isEmpty()) {
            throw new BusinessRuleException(
                    "Complete the due diligence checklist before underwriting",
                    "ANCHOR_DD_INCOMPLETE",
                    "SAVE_DUE_DILIGENCE",
                    null);
        }
        List<String> questionKeys = ratingPolicyEngine.questionKeys(ratingPolicyEngine.activeConfig());
        for (String key : questionKeys) {
            if (!answers.containsKey(key) || String.valueOf(answers.get(key)).isBlank()) {
                throw new BusinessRuleException(
                        "All due diligence questions must be answered",
                        "ANCHOR_DD_INCOMPLETE",
                        "SAVE_DUE_DILIGENCE",
                        Map.of("missing", key));
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> comments = block.get("comments") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : Map.of();
        block = ratingPolicyEngine.computeBlock(answers, comments, true);
        findActiveTemplateMeta(block);
        block.put("completedAt", Instant.now().toString());
        block.put("updatedAt", Instant.now().toString());
        mergeDueDiligence(app, block);
        String rating = String.valueOf(block.get("creditRating"));
        int score = ((Number) block.get("score")).intValue();

        app.setCreditRiskScore(score);
        app.setBureauScore(null);
        app.setManualBureauScore(null);

        if ("D".equals(rating)) {
            app.setCreditDecision("REJECTED");
            app.setStatus(ApplicationStatus.REJECTED);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
            auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_COMPLETE", null, null,
                    Map.of("decision", "REJECTED", "creditRating", rating, "score", String.valueOf(score)),
                    "Anchor underwriting rejected — rating D");
            return underwritingResult(app, enrichForUi(block), "REJECTED");
        }

        if ("C".equals(rating)) {
            app.setCreditDecision("MANUAL_REVIEW");
            app.setStatus(ApplicationStatus.UNDERWRITING);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
            auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_COMPLETE", null, null,
                    Map.of("decision", "MANUAL_REVIEW", "creditRating", rating, "score", String.valueOf(score)),
                    "Anchor underwriting referred — rating C");
            return underwritingResult(app, enrichForUi(block), "MANUAL_REVIEW");
        }

        app.setCreditDecision("APPROVED");
        app.setStatus(ApplicationStatus.SANCTION_PENDING);
        if (app.getSanctionedAmount() == null && app.getRequestedAmount() != null) {
            app.setSanctionedAmount(app.getRequestedAmount());
        }
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        if ("APPROVED".equals(app.getCreditDecision())) {
            triggerPlpAnchorPush(applicationId);
        }
        auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_COMPLETE", null, null,
                Map.of("decision", "APPROVED", "creditRating", rating, "score", String.valueOf(score),
                        "status", "SANCTION_PENDING"),
                "Anchor underwriting approved — proceed to sanction");
        return underwritingResult(app, enrichForUi(block), "APPROVED");
    }

    @Transactional
    public LoanApplication resolveManualReview(UUID applicationId, boolean approve) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        if (app.getStatus() != ApplicationStatus.UNDERWRITING
                || !"MANUAL_REVIEW".equals(app.getCreditDecision())) {
            throw new BusinessRuleException(
                    "No pending anchor manual review. Current: " + app.getStatus()
                            + " / " + app.getCreditDecision());
        }
        if (approve) {
            app.setCreditDecision("APPROVED");
            app.setStatus(ApplicationStatus.SANCTION_PENDING);
            if (app.getSanctionedAmount() == null && app.getRequestedAmount() != null) {
                app.setSanctionedAmount(app.getRequestedAmount());
            }
        } else {
            app.setCreditDecision("REJECTED");
            app.setStatus(ApplicationStatus.REJECTED);
        }
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        if (approve) {
            triggerPlpAnchorPush(applicationId);
        }
        auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_MANUAL_RESOLVED", null, null,
                Map.of("decision", app.getCreditDecision(), "status", app.getStatus().name()),
                "Anchor manual underwriting " + (approve ? "approved" : "rejected"));
        return app;
    }

    /** Backward-compatible scoring for unit tests (uses built-in default template). */
    static Map<String, Object> computeRatingBlock(Map<String, Object> answers, boolean requireComplete) {
        Map<String, Object> block = AnchorRatingPolicyEngine.computeBlock(
                AnchorRatingPolicyEngine.defaultConfig(), answers, Map.of());
        if (requireComplete) {
            block.put("completedAt", Instant.now().toString());
        }
        block.put("updatedAt", Instant.now().toString());
        return block;
    }

    private void findActiveTemplateMeta(Map<String, Object> block) {
        ratingPolicyEngine.findActiveTemplate().ifPresent(t -> {
            block.put("templateId", t.getId().toString());
            block.put("templateVersion", t.getVersion());
        });
    }

    private Map<String, Object> enrichForUi(Map<String, Object> block) {
        Map<String, Object> out = new LinkedHashMap<>(block);
        out.put("questions", ratingPolicyEngine.questionsForUi());
        out.put("ratingBands", ratingPolicyEngine.ratingBandsForUi());
        return out;
    }

    private void triggerPlpAnchorPush(UUID applicationId) {
        try {
            plpAnchorSanctionHookService.onAnchorCreditRatingCompleted(applicationId);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AnchorDueDiligenceService.class)
                    .error("[PLP-ANCHOR-RATING] PLP anchor sync failed for {} — credit rating decision preserved: {}",
                            applicationId, e.getMessage(), e);
        }
    }

    private Map<String, Object> underwritingResult(LoanApplication app, Map<String, Object> block, String decision) {
        Map<String, Object> out = new LinkedHashMap<>(block);
        out.put("applicationId", app.getId());
        out.put("applicationNumber", app.getApplicationNumber());
        out.put("status", app.getStatus().name());
        out.put("decision", decision);
        out.put("creditDecision", app.getCreditDecision());
        return out;
    }

    private void assertAnchorFlow(LoanApplication app) {
        if (!InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            throw new BusinessRuleException(
                    "Due diligence underwriting applies only to invoice discounting anchor applications",
                    "NOT_ANCHOR_FLOW",
                    "OPEN_APPLICATION",
                    null);
        }
    }

    private void assertKycReady(LoanApplication app, UUID applicationId) {
        Map<String, Object> kycOutcome = kycOrchestrationService.computeKycOutcome(applicationId);
        String outcome = String.valueOf(kycOutcome.getOrDefault("outcome", "INCOMPLETE"));
        if (!"PASS".equalsIgnoreCase(outcome)) {
            throw new BusinessRuleException(
                    "KYC must pass before anchor due diligence",
                    "KYC_OUTCOME_NOT_PASS",
                    "COMPLETE_KYC",
                    Map.of("kycOutcome", outcome));
        }
        if (app.getStatus() != ApplicationStatus.KYC_IN_PROGRESS
                && app.getStatus() != ApplicationStatus.UNDERWRITING
                && app.getStatus() != ApplicationStatus.SANCTION_PENDING) {
            if (!(app.getStatus() == ApplicationStatus.UNDERWRITING && "MANUAL_REVIEW".equals(app.getCreditDecision()))) {
                throw new BusinessRuleException(
                        "Due diligence is not available in status " + app.getStatus(),
                        "INVALID_STATUS",
                        "OPEN_APPLICATION",
                        Map.of("status", app.getStatus().name()));
            }
        }
    }

    private LoanApplication findOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readDueDiligenceBlock(LoanApplication app) {
        Map<String, Object> fi = app.getFinancialInfo();
        if (fi == null) {
            return emptyBlock();
        }
        Object raw = fi.get("anchorDueDiligence");
        if (!(raw instanceof Map<?, ?> map)) {
            return emptyBlock();
        }
        Map<String, Object> block = new LinkedHashMap<>((Map<String, Object>) map);
        if (!(block.get("comments") instanceof Map<?, ?>)) {
            block.put("comments", new LinkedHashMap<>());
        }
        return block;
    }

    private static Map<String, Object> emptyBlock() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answers", new LinkedHashMap<>());
        m.put("comments", new LinkedHashMap<>());
        m.put("creditRating", "");
        m.put("score", 0);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractAnswers(Map<String, Object> body) {
        if (body == null) return Map.of();
        if (body.get("answers") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!"comments".equals(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractComments(Map<String, Object> body) {
        if (body == null || !(body.get("comments") instanceof Map<?, ?> m)) {
            return Map.of();
        }
        return (Map<String, Object>) m;
    }

    private static Map<String, Object> normalizeAnswers(Map<String, Object> answers) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (answers != null) {
            for (Map.Entry<String, Object> e : answers.entrySet()) {
                if (e.getValue() != null && !"comments".equals(e.getKey())) {
                    out.put(e.getKey(), String.valueOf(e.getValue()).trim());
                }
            }
        }
        return out;
    }

    private static Map<String, Object> normalizeComments(Map<String, Object> comments) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (comments != null) {
            for (Map.Entry<String, Object> e : comments.entrySet()) {
                if (e.getValue() != null) {
                    String trimmed = String.valueOf(e.getValue()).trim();
                    if (!trimmed.isBlank()) {
                        out.put(e.getKey(), trimmed);
                    }
                }
            }
        }
        return out;
    }

    private static void mergeDueDiligence(LoanApplication app, Map<String, Object> block) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo()) : new LinkedHashMap<>();
        fi.put("anchorDueDiligence", block);
        app.setFinancialInfo(fi);
    }
}
