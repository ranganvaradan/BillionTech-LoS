package com.los.core.service.borrower;

import com.los.core.config.RabbitMQConfig;
import com.los.core.config.LosWorkflowProperties;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.auth.BorrowerAccountProvisioningService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.loan.InvoiceDiscountingSanctionDefaultsService;
import com.los.core.service.loan.LoanApplicationFlowService;
import com.los.core.service.loan.ApplicationInputChangeTracker;
import com.los.core.service.integration.itr.ItrWorkflowRequirement;
import com.los.core.service.integration.gstanalysis.GstAnalysisService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowerIntakeDelegationService {

    private final LoanApplicationRepository applicationRepository;
    private final BorrowerAccountProvisioningService borrowerAccountProvisioningService;
    private final AuditService auditService;
    private final RabbitTemplate rabbitTemplate;
    private final LosWorkflowProperties workflowProperties;
    private final InvoiceDiscountingSanctionDefaultsService invoiceDiscountingSanctionDefaultsService;
    private final ApplicationInputChangeTracker applicationInputChangeTracker;
    private final ItrWorkflowRequirement itrWorkflowRequirement;
    private final GstAnalysisService gstAnalysisService;

    @Value("${los.borrower-ui-url:http://localhost:5173/borrower}")
    private String borrowerUiUrl;

    @Transactional
    public LoanApplication saveDraftAndNotifyBorrower(UUID applicationId, int completedStep) {
        if (!workflowProperties.getBorrowerDelegation().isEnabled()) {
            throw new BusinessRuleException("Borrower intake delegation is disabled");
        }
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            throw new BusinessRuleException("Anchor onboarding cannot be delegated to borrower portal");
        }
        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.CONSENT_PENDING) {
            throw new BusinessRuleException(
                    "Save draft & notify is only allowed from DRAFT or CONSENT_PENDING. Current: "
                            + app.getStatus());
        }

        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            throw new BusinessRuleException("Borrower email is required before notifying the borrower");
        }
        String name = ApplicationPartyResolver.resolveDisplayName(app);
        String mobile = ApplicationPartyResolver.resolveMobile(app);

        Optional<BorrowerAccountProvisioningService.BorrowerProvisionResult> provisioned =
                borrowerAccountProvisioningService.findOrCreateBorrowerWithCredential(name, email, mobile);
        if (provisioned.isPresent()) {
            app.setCustomerId(provisioned.get().user().getId());
        }

        app.setStatus(ApplicationStatus.CONSENT_PENDING);
        app.setIntakeOwner(IntakeOwner.BORROWER);
        app.setIntakeCompletedStep(completedStep);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "BORROWER_INTAKE_DELEGATED", null,
                Map.of("status", ApplicationStatus.DRAFT.name()),
                Map.of("status", ApplicationStatus.CONSENT_PENDING.name(), "completedStep", completedStep),
                "Staff saved draft and invited borrower to complete intake");

        String tempPassword = provisioned
                .map(BorrowerAccountProvisioningService.BorrowerProvisionResult::temporaryPasswordForEmail)
                .filter(p -> p != null && !p.isBlank())
                .orElse("Use your existing password or forgot-password to reset");

        publishInviteEmail(app, email, name, tempPassword);
        return app;
    }

    @Transactional
    public LoanApplication submitBorrowerIntake(UUID applicationId, UUID borrowerUserId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (borrowerUserId != null && !borrowerUserId.equals(app.getCustomerId())) {
            throw new BusinessRuleException("You can only submit your own application");
        }
        if (app.getStatus() != ApplicationStatus.CONSENT_PENDING
                && app.getStatus() != ApplicationStatus.BORROWER_SENT_BACK) {
            throw new BusinessRuleException(
                    "Borrower submit requires CONSENT_PENDING or BORROWER_SENT_BACK. Current: "
                            + app.getStatus());
        }
        if (app.getIntakeOwner() != null && app.getIntakeOwner() != IntakeOwner.BORROWER) {
            throw new BusinessRuleException("This application was not delegated to the borrower portal");
        }
        // Heal older rows notified before intakeOwner was persisted/exposed on the API.
        if (app.getIntakeOwner() == null) {
            app.setIntakeOwner(IntakeOwner.BORROWER);
        }
        // Reject a requested amount above the program's Max. dealer limit before it reaches sanction / PLP link.
        invoiceDiscountingSanctionDefaultsService.validateRequestedAmountWithinProgramLimit(app);
        itrWorkflowRequirement.requireSuccessIfMandatory(app, "submitting for review");
        gstAnalysisService.requirePreparedIfMandatory(app, "submitting for review");
        // Karza GST docs upload is admin-driven on application review (not on borrower submit).
        applicationInputChangeTracker.refreshIntakeChangeSinceSendBack(app);
        app.setStatus(ApplicationStatus.BORROWER_SUBMITTED);
        app.setBorrowerSentBackNotes(null);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "BORROWER_SUBMITTED", null,
                Map.of("status", ApplicationStatus.CONSENT_PENDING.name()),
                Map.of("status", ApplicationStatus.BORROWER_SUBMITTED.name()),
                "Borrower completed delegated intake and submitted for admin review");
        return app;
    }

    private void publishInviteEmail(LoanApplication app, String email, String name, String tempPasswordHint) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("borrowerName", name != null && !name.isBlank() ? name : "Customer");
        data.put("applicationNumber", app.getApplicationNumber());
        data.put("portalUrl", borrowerUiUrl + "/apply?resume=" + app.getId());
        data.put("loginEmail", email);
        data.put("temporaryPassword", tempPasswordHint);

        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(email.trim())
                .templateCode("BORROWER_INTAKE_INVITE")
                .eventType("BORROWER_INTAKE")
                .applicationId(app.getId())
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE, "notification.email.borrower_intake", event);
        } catch (Exception e) {
            log.error("Borrower intake invite email failed for {}: {}", app.getId(), e.getMessage());
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
