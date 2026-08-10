package com.los.core.service.loan;

import com.los.core.config.RabbitMQConfig;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.CreditAppraisalMemoRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationReviewService {

    private final LoanApplicationRepository applicationRepository;
    private final CreditAppraisalMemoRepository creditAppraisalMemoRepository;
    private final ILoanApplicationService loanApplicationService;
    private final AuditService auditService;
    private final RabbitTemplate rabbitTemplate;
    private final WorkflowRoleGuard workflowRoleGuard;
    private final ApplicationInputChangeTracker applicationInputChangeTracker;

    @Value("${los.borrower-ui-url:http://localhost:5173/borrower}")
    private String borrowerUiUrl;

    /**
     * Statuses after accept / during processing where optional send-back remains available,
     * until sanction or eSign onboarding begins ({@code SANCTIONED} / {@code ESIGN_PENDING}+).
     */
    private static final Set<ApplicationStatus> PRE_SANCTION_SEND_BACK = EnumSet.of(
            ApplicationStatus.KYC_IN_PROGRESS,
            ApplicationStatus.KYC_FAILED,
            ApplicationStatus.UNDERWRITING,
            ApplicationStatus.UNDERWRITING_COMPLETED,
            ApplicationStatus.CAM_READY,
            ApplicationStatus.CAM_SENT_BACK,
            ApplicationStatus.CAM_REVIEWED,
            ApplicationStatus.SANCTION_PENDING,
            ApplicationStatus.APPROVED);

    @Transactional
    public ApplicationResponse acceptForProcessing(UUID applicationId, String userRole) {
        LoanApplication app = requireApp(applicationId);
        ApplicationStatus status = app.getStatus();
        String role = normalizeRole(userRole);

        if (status == ApplicationStatus.PENDING_CREDIT_OFFICER) {
            workflowRoleGuard.requireCreditOfficerOrAdmin(userRole);
        } else if (status == ApplicationStatus.BORROWER_SUBMITTED) {
            // Admin break-glass: accept without RM handoff
            if (!isAdmin(role)) {
                throw new BusinessRuleException(
                        "Accept from BORROWER_SUBMITTED requires Admin. "
                                + "Credit Officer must wait for RM handoff (PENDING_CREDIT_OFFICER). Current: "
                                + status);
            }
        } else {
            throw new BusinessRuleException(
                    "Accept is only for PENDING_CREDIT_OFFICER (or Admin from BORROWER_SUBMITTED). Current: "
                            + status);
        }

        ApplicationStatus from = status;
        app.setStatus(ApplicationStatus.KYC_IN_PROGRESS);
        app.setIntakeOwner(IntakeOwner.STAFF);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "BORROWER_REVIEW_ACCEPTED", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.KYC_IN_PROGRESS.name()),
                "Accepted application for KYC processing");
        return loanApplicationService.getApplication(applicationId);
    }

    @Transactional
    public ApplicationResponse sendBackToBorrower(UUID applicationId, String notes, String userRole) {
        workflowRoleGuard.requireRelationshipManagerOrAdmin(userRole);
        LoanApplication app = requireApp(applicationId);
        if (app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            throw new BusinessRuleException(
                    "Send back to borrower is not available for anchor applications. "
                            + "Credit Officer may still send the case back to the Relationship Manager.",
                    "ANCHOR_NO_BORROWER_SEND_BACK",
                    "SEND_BACK_TO_BORROWER",
                    Map.of("intakeSegment", "ANCHOR"));
        }
        ApplicationStatus from = app.getStatus();
        if (!allowsSendBackToBorrower(from)) {
            throw new BusinessRuleException(
                    "Send back to borrower is only from BORROWER_SUBMITTED or SENT_BACK_TO_RM. Current: "
                            + from);
        }
        String trimmed = trimToNull(notes);
        StatusChangeContext.set(null, trimmed != null ? trimmed : "Sent back to borrower");
        try {
            app.setStatus(ApplicationStatus.BORROWER_SENT_BACK);
            app.setBorrowerSentBackNotes(trimmed);
            app.setIntakeOwner(IntakeOwner.BORROWER);
            storeReviewNotes(app, "SENT_BACK_TO_BORROWER", trimmed);
            applicationInputChangeTracker.snapshotIntakeAtSendBack(app);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
        } finally {
            StatusChangeContext.clear();
        }
        auditService.logEvent(applicationId, "FLOW", "BORROWER_SENT_BACK", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.BORROWER_SENT_BACK.name(),
                        "notes", trimmed != null ? trimmed : ""),
                "Sent application back to borrower for more details");
        publishSendBackEmail(app, trimmed != null ? trimmed : "");
        return loanApplicationService.getApplication(applicationId);
    }

    @Transactional
    public ApplicationResponse handOffToCreditOfficer(UUID applicationId, String notes, String userRole) {
        workflowRoleGuard.requireRelationshipManagerOrAdmin(userRole);
        LoanApplication app = requireApp(applicationId);
        ApplicationStatus status = app.getStatus();
        if (status != ApplicationStatus.BORROWER_SUBMITTED
                && status != ApplicationStatus.SENT_BACK_TO_RM) {
            throw new BusinessRuleException(
                    "Hand off to Credit Officer is only from BORROWER_SUBMITTED or SENT_BACK_TO_RM. Current: "
                            + status);
        }
        String trimmed = trimToNull(notes);
        ApplicationStatus from = status;
        app.setStatus(ApplicationStatus.PENDING_CREDIT_OFFICER);
        app.setIntakeOwner(IntakeOwner.STAFF);
        storeReviewNotes(app, "HANDOFF_TO_CREDIT_OFFICER", trimmed);
        applicationInputChangeTracker.refreshIntakeChangeSinceSendBack(app);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "HANDOFF_TO_CREDIT_OFFICER", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.PENDING_CREDIT_OFFICER.name(),
                        "notes", trimmed != null ? trimmed : ""),
                "RM handed application to Credit Officer");
        return loanApplicationService.getApplication(applicationId);
    }

    @Transactional
    public ApplicationResponse sendBackToRelationshipManager(
            UUID applicationId, String notes, String userRole) {
        workflowRoleGuard.requireCreditOfficerOrAdmin(userRole);
        LoanApplication app = requireApp(applicationId);
        ApplicationStatus from = app.getStatus();
        if (!allowsSendBackToRm(from)) {
            throw new BusinessRuleException(
                    "Send back to RM is only from PENDING_CREDIT_OFFICER or processing statuses "
                            + "before sanction/eSign. Current: " + from);
        }
        if (from == ApplicationStatus.CAM_READY && isCamSubmittedToManager(applicationId)) {
            throw new BusinessRuleException(
                    "Send back to RM is hidden once the CAM is submitted to Credit Manager. "
                            + "Use the Credit Manager send-back flow to return the case first.",
                    "CAM_ALREADY_SUBMITTED_TO_MANAGER",
                    "SEND_BACK_TO_RM",
                    Map.of("status", from.name()));
        }
        String trimmed = trimToNull(notes);
        StatusChangeContext.set(null, trimmed != null ? trimmed : "Sent back to Relationship Manager");
        try {
            app.setStatus(ApplicationStatus.SENT_BACK_TO_RM);
            app.setIntakeOwner(IntakeOwner.STAFF);
            storeReviewNotes(app, "SENT_BACK_TO_RM", trimmed);
            applicationInputChangeTracker.snapshotIntakeAtSendBack(app);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
        } finally {
            StatusChangeContext.clear();
        }
        auditService.logEvent(applicationId, "FLOW", "SENT_BACK_TO_RM", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.SENT_BACK_TO_RM.name(),
                        "notes", trimmed != null ? trimmed : ""),
                "Credit Officer sent application back to Relationship Manager");
        return loanApplicationService.getApplication(applicationId);
    }

    private static boolean allowsSendBackToBorrower(ApplicationStatus status) {
        return status == ApplicationStatus.BORROWER_SUBMITTED
                || status == ApplicationStatus.SENT_BACK_TO_RM;
    }

    private static boolean allowsSendBackToRm(ApplicationStatus status) {
        return status == ApplicationStatus.PENDING_CREDIT_OFFICER
                || PRE_SANCTION_SEND_BACK.contains(status);
    }

    private boolean isCamSubmittedToManager(UUID applicationId) {
        return creditAppraisalMemoRepository.findByApplicationId(applicationId)
                .map(cam -> "SUBMITTED".equalsIgnoreCase(cam.getCamStatus()))
                .orElse(false);
    }

    private LoanApplication requireApp(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
    }

    private static void storeReviewNotes(LoanApplication app, String action, String notes) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo())
                : new LinkedHashMap<>();
        Map<String, Object> reviewNotes = new LinkedHashMap<>();
        reviewNotes.put("lastAction", action);
        if (notes != null && !notes.isBlank()) {
            reviewNotes.put("notes", notes);
        }
        reviewNotes.put("at", Instant.now().toString());
        fi.put("reviewNotes", reviewNotes);
        app.setFinancialInfo(fi);
    }

    private static String trimToNull(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }

    private static String normalizeRole(String userRole) {
        return userRole == null ? "" : userRole.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equals(role) || "ADMINISTRATOR".equals(role);
    }

    private void publishSendBackEmail(LoanApplication app, String notes) {
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("borrowerName", ApplicationPartyResolver.resolveDisplayName(app));
        data.put("applicationNumber", app.getApplicationNumber());
        data.put("notes", notes);
        data.put("portalUrl", borrowerUiUrl + "/apply?resume=" + app.getId());

        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(email.trim())
                .templateCode("BORROWER_APPLICATION_SENT_BACK")
                .eventType("BORROWER_SENT_BACK")
                .applicationId(app.getId())
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE, "notification.email.borrower_sent_back", event);
        } catch (Exception e) {
            log.error("Send-back email failed for {}: {}", app.getId(), e.getMessage());
        }
    }

    @Data
    @Builder
    private static final class RoutingEmailEvent {
        private String channel;
        private String recipient;
        private String templateCode;
        private String eventType;
        private Map<String, Object> templateData;
        private UUID applicationId;
    }
}
