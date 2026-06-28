package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.DeleteApplicationRequest;
import com.los.core.model.dto.response.ApplicationDeletionPreviewResponse;
import com.los.core.model.entity.ApplicationDeletionLog;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.ApplicationDeletionLogRepository;
import com.los.core.repository.CreditAppraisalMemoRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.ProcessOverrideHistoryRepository;
import com.los.core.repository.UnderwritingEvaluationRepository;
import com.los.core.repository.UserRoleMappingRepository;
import com.los.core.payment.repository.LosLoanPaymentInProgressRepository;
import com.los.core.payment.repository.LosLoanPaymentTransactionRepository;
import com.los.core.service.demo.DemoApplicationPurgeService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.lms.repository.LmsAccountSummaryRepository;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.repository.LmsRepaymentCallbackRepository;
import com.los.plp.service.PlpApplicationCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationDeletionService {

    private static final Set<ApplicationStatus> ACTIVE_LOAN_WARNING_STATUSES = EnumSet.of(
            ApplicationStatus.SANCTIONED,
            ApplicationStatus.KFS_GENERATED,
            ApplicationStatus.ESIGN_PENDING,
            ApplicationStatus.ESIGN_COMPLETED,
            ApplicationStatus.READY_FOR_DISBURSEMENT,
            ApplicationStatus.DISBURSEMENT_PENDING,
            ApplicationStatus.DISBURSED);

    private static final Set<String> DELETE_ALLOWED_ROLES = Set.of(
            "ADMIN",
            "ADMINISTRATOR",
            "CREDIT_MANAGER",
            "CREDIT_OFFICER",
            "PLATFORM_ADMIN");

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicationDeletionLogRepository deletionLogRepository;
    private final DemoApplicationPurgeService demoApplicationPurgeService;
    private final PlpApplicationCleanupService plpApplicationCleanupService;
    private final ProcessOverrideHistoryRepository processOverrideHistoryRepository;
    private final CreditAppraisalMemoRepository creditAppraisalMemoRepository;
    private final UnderwritingEvaluationRepository underwritingEvaluationRepository;
    private final LmsLoanHandoverRepository lmsLoanHandoverRepository;
    private final LmsRepaymentCallbackRepository lmsRepaymentCallbackRepository;
    private final LmsAccountSummaryRepository lmsAccountSummaryRepository;
    private final LosLoanPaymentTransactionRepository losLoanPaymentTransactionRepository;
    private final LosLoanPaymentInProgressRepository losLoanPaymentInProgressRepository;
    private final LosUserRepository losUserRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;

    public ApplicationDeletionPreviewResponse previewDeletion(UUID applicationId) {
        LoanApplication app = requireBorrowerApplication(applicationId);
        return buildPreview(app);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(
            UUID applicationId,
            DeleteApplicationRequest request,
            UUID deletedByUserId,
            String deletedByEmail,
            String deletedByRole) {
        assertDeleteRole(deletedByRole);
        LoanApplication app = requireBorrowerApplication(applicationId);
        ApplicationDeletionPreviewResponse preview = buildPreview(app);
        if (preview.isRequiresDoubleConfirm()
                && (request == null || !request.isConfirmActiveLoan())) {
            throw new BusinessRuleException(
                    preview.getWarningMessage() != null
                            ? preview.getWarningMessage()
                            : "Confirm active-loan deletion before proceeding.",
                    "APPLICATION_DELETE_ACTIVE_LOAN_CONFIRM_REQUIRED",
                    "CONFIRM_ACTIVE_LOAN",
                    null);
        }

        String plpSummary = null;
        boolean plpAttempted = false;
        if (preview.isPlpLinked()) {
            plpAttempted = true;
            plpSummary = plpApplicationCleanupService.cleanupForApplication(app);
        }

        ApplicationDeletionLog logEntry = ApplicationDeletionLog.builder()
                .applicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .loanProduct(app.getLoanProduct())
                .intakeSegment(app.getIntakeSegment() != null ? app.getIntakeSegment().name() : IntakeSegment.BORROWER.name())
                .applicationStatus(app.getStatus() != null ? app.getStatus().name() : "UNKNOWN")
                .borrowerUserId(app.getCustomerId())
                .borrowerEmail(resolveBorrowerEmail(app))
                .plpBorrowerId(app.getPlpBorrowerId())
                .deletedByUserId(deletedByUserId)
                .deletedByEmail(deletedByEmail)
                .deletedByRole(deletedByRole)
                .reason(request != null ? request.getReason() : null)
                .plpCleanupAttempted(plpAttempted)
                .plpCleanupSummary(plpSummary)
                .snapshotJson(buildSnapshot(app, preview))
                .build();
        deletionLogRepository.save(logEntry);

        UUID borrowerUserId = app.getCustomerId();
        deleteExtendedDependents(app);
        demoApplicationPurgeService.deleteApplicationDependentsByIds(List.of(app.getId()));
        maybeDeleteBorrowerUser(borrowerUserId, app.getId());

        log.info(
                "Application deleted: {} ({}) by {} — PLP cleanup={}",
                app.getApplicationNumber(),
                app.getId(),
                deletedByEmail,
                plpAttempted);
    }

    private void deleteExtendedDependents(LoanApplication app) {
        UUID applicationId = app.getId();
        String applicationNumber = app.getApplicationNumber();
        processOverrideHistoryRepository.deleteByApplicationId(applicationId);
        creditAppraisalMemoRepository.deleteByApplicationId(applicationId);
        underwritingEvaluationRepository.deleteByApplicationId(applicationId);
        losLoanPaymentInProgressRepository.deleteByApplicationId(applicationId);
        losLoanPaymentTransactionRepository.deleteByApplicationId(applicationId);
        if (applicationNumber != null && !applicationNumber.isBlank()) {
            lmsRepaymentCallbackRepository.deleteByApplicationNumber(applicationNumber);
            lmsAccountSummaryRepository.deleteByApplicationNumber(applicationNumber);
            lmsLoanHandoverRepository.deleteByApplicationNumber(applicationNumber);
        }
    }

    private void maybeDeleteBorrowerUser(UUID borrowerUserId, UUID deletedApplicationId) {
        if (borrowerUserId == null) {
            return;
        }
        long remaining = loanApplicationRepository.countByCustomerId(borrowerUserId);
        if (remaining > 0) {
            return;
        }
        LosUser user = losUserRepository.findById(borrowerUserId).orElse(null);
        if (user == null || !"BORROWER".equalsIgnoreCase(user.getPrimaryLosRole())) {
            return;
        }
        userRoleMappingRepository.deleteByUserId(borrowerUserId);
        losUserRepository.delete(user);
        log.info("Deleted borrower LOS user {} after application purge", borrowerUserId);
    }

    private LoanApplication requireBorrowerApplication(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            throw new BusinessRuleException(
                    "Anchor applications cannot be deleted from this screen.",
                    "APPLICATION_DELETE_ANCHOR_NOT_ALLOWED",
                    "BORROWER_ONLY",
                    null);
        }
        return app;
    }

    private ApplicationDeletionPreviewResponse buildPreview(LoanApplication app) {
        boolean invoiceDiscountingBorrower = InvoiceDiscountingApplicationRules.isBorrowerFlow(app);
        boolean plpLinked = invoiceDiscountingBorrower && isPlpLinked(app);
        boolean requiresDoubleConfirm = !invoiceDiscountingBorrower
                && app.getStatus() != null
                && ACTIVE_LOAN_WARNING_STATUSES.contains(app.getStatus());
        String warningMessage = null;
        if (requiresDoubleConfirm) {
            warningMessage = "This application has an active loan lifecycle state ("
                    + app.getStatus().name()
                    + "). Deleting will remove the borrower user, loan records, repayments, and related data. "
                    + "Confirm only if you intend to permanently remove this case.";
        }
        String summaryMessage = plpLinked
                ? "This invoice discounting borrower is linked to PLP. Deletion will remove the PLP borrower, program links, loans, and invoices where applicable."
                : "This will permanently delete the application and all related LOS records.";
        return ApplicationDeletionPreviewResponse.builder()
                .applicationId(app.getId().toString())
                .applicationNumber(app.getApplicationNumber())
                .loanProduct(app.getLoanProduct())
                .intakeSegment(app.getIntakeSegment() != null ? app.getIntakeSegment().name() : IntakeSegment.BORROWER.name())
                .status(app.getStatus() != null ? app.getStatus().name() : null)
                .borrowerApplication(true)
                .invoiceDiscountingBorrower(invoiceDiscountingBorrower)
                .plpLinked(plpLinked)
                .requiresDoubleConfirm(requiresDoubleConfirm)
                .warningMessage(warningMessage)
                .summaryMessage(summaryMessage)
                .build();
    }

    private static boolean isPlpLinked(LoanApplication app) {
        return app.getPlpBorrowerId() != null
                || app.getSubProgramId() != null
                || app.getPlpSubProgramBorrowerId() != null
                || app.getPlpBorrowerProgramMappingId() != null;
    }

    private static Map<String, Object> buildSnapshot(
            LoanApplication app, ApplicationDeletionPreviewResponse preview) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("applicationNumber", app.getApplicationNumber());
        snapshot.put("loanProduct", app.getLoanProduct());
        snapshot.put("status", app.getStatus() != null ? app.getStatus().name() : null);
        snapshot.put("customerId", app.getCustomerId() != null ? app.getCustomerId().toString() : null);
        snapshot.put("plpBorrowerId", app.getPlpBorrowerId() != null ? app.getPlpBorrowerId().toString() : null);
        snapshot.put("subProgramId", app.getSubProgramId() != null ? app.getSubProgramId().toString() : null);
        snapshot.put("requiresDoubleConfirm", preview.isRequiresDoubleConfirm());
        snapshot.put("plpLinked", preview.isPlpLinked());
        return snapshot;
    }

    private static String resolveBorrowerEmail(LoanApplication app) {
        if (app.getPersonalInfo() != null) {
            Object email = app.getPersonalInfo().get("email");
            if (email != null && !String.valueOf(email).isBlank()) {
                return String.valueOf(email).trim();
            }
        }
        return null;
    }

    private static void assertDeleteRole(String role) {
        if (role == null || role.isBlank()) {
            throw new BusinessRuleException(
                    "Application deletion requires an authenticated staff role.",
                    "APPLICATION_DELETE_FORBIDDEN",
                    "FORBIDDEN",
                    null);
        }
        if (!DELETE_ALLOWED_ROLES.contains(role.trim())) {
            throw new BusinessRuleException(
                    "You do not have permission to delete applications.",
                    "APPLICATION_DELETE_FORBIDDEN",
                    "FORBIDDEN",
                    null);
        }
    }
}
