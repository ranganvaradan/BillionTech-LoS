package com.los.core.service.loan;

import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.customercategory.selection.CategoryPropositionConfig;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.dto.request.ManualCreditInputsRequest;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.request.ValidateIdentityRequest;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.CreditAppraisalMemoRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.service.audit.AuditService;
import com.los.core.service.audit.RecordAuditService;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.loan.intake.ApplicationSubmitIdentityValidator;
import com.los.core.service.loan.intake.ApplicationCustomerIdResolver;
import com.los.core.service.loan.intake.AnchorIntakeValidation;
import com.los.core.service.loan.intake.IntakeMetadataEnricher;
import com.los.core.service.underwriting.UnderwritingEvaluationService;
import com.los.core.service.workflow.ApplicationConfigurationAuthority;
import com.los.core.service.workflow.ApplicationWorkflowResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationServiceImpl implements ILoanApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final AuditService auditService;
    private final RecordAuditService recordAuditService;
    private final CreditControlService creditControlService;
    private final UnderwritingEvaluationService underwritingEvaluationService;
    private final CreditAppraisalMemoRepository creditAppraisalMemoRepository;
    private final ApplicationCustomerIdResolver applicationCustomerIdResolver;
    private final IntakeMetadataEnricher intakeMetadataEnricher;
    private final ApplicationSubmitIdentityValidator applicationSubmitIdentityValidator;
    private final ApplicationInputChangeTracker applicationInputChangeTracker;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final ApplicationWorkflowResolver applicationWorkflowResolver;
    private final CustomerCategoryRepository customerCategoryRepository;

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Override
    @Transactional
    public ApplicationResponse setManualBureau(UUID applicationId, Integer manualBureauScore, String manualBureauRemarks, UUID manualBureauDocumentId, UUID performedBy) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        Map<String, Object> previous = Map.of(
                "manualBureauScore", app.getManualBureauScore() != null ? app.getManualBureauScore() : 0,
                "manualBureauRemarks", app.getManualBureauRemarks() != null ? app.getManualBureauRemarks() : "",
                "manualBureauDocumentId", app.getManualBureauDocumentId() != null ? app.getManualBureauDocumentId().toString() : ""
        );

        app.setManualBureauScore(manualBureauScore);
        app.setManualBureauRemarks(manualBureauRemarks);
        app.setManualBureauDocumentId(manualBureauDocumentId);
        app = applicationRepository.save(app);

        Map<String, Object> next = Map.of(
                "manualBureauScore", app.getManualBureauScore() != null ? app.getManualBureauScore() : 0,
                "manualBureauRemarks", app.getManualBureauRemarks() != null ? app.getManualBureauRemarks() : "",
                "manualBureauDocumentId", app.getManualBureauDocumentId() != null ? app.getManualBureauDocumentId().toString() : ""
        );

        auditService.logEvent(applicationId,
                "MANUAL_BUREAU_SAVE",
                "MANUAL_BUREAU_SAVE",
                performedBy,
                previous,
                next,
                "Manual bureau override saved");

        return enrich(toResponse(app), app);
    }

    @Override
    @Transactional
    public ApplicationResponse applyManualCreditInputs(UUID applicationId, ManualCreditInputsRequest request, UUID performedBy) {
        LoanApplication app = findApplicationOrThrow(applicationId);
        if (request != null) {
            creditControlService.mergeManualInputs(app, request);
        }
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "CREDIT_CONTROL", "MANUAL_CREDIT_INPUTS",
                performedBy, null,
                Map.of("saved", true),
                "Manual credit inputs merged under financialInfo.creditControl");
        return enrich(toResponse(app), app);
    }

    @Override
    @Transactional
    public ApplicationResponse applyScorecardInputs(UUID applicationId, ManualCreditInputsRequest request, UUID performedBy) {
        LoanApplication app = findApplicationOrThrow(applicationId);
        if (request != null) {
            creditControlService.mergeScorecardInputsOnly(app, request);
        }
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "CREDIT_CONTROL", "SCORECARD_INPUTS",
                performedBy, null,
                Map.of("saved", true),
                "Scorecard underwriting inputs merged under financialInfo.creditControl");
        return enrich(toResponse(app), app);
    }

    @Override
    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, UUID actingUserId, String actingUserRole) {
        AnchorIntakeValidation.validateCreate(request);
        AnchorIntakeValidation.rejectBorrowerSelfServiceAnchor(request);
        requireBorrowerIntakeIfDeclared(request, actingUserId, actingUserRole);
        validateCreateEmailForIndividual(request);
        validateCreateEmailForAnchor(request);
        String applicationNumber = generateApplicationNumber(
                request.getBorrowerType(), AnchorIntakeValidation.resolveSegment(request));

        UUID customerId = applicationCustomerIdResolver.resolveCustomerId(request, actingUserId, actingUserRole);
        Map<String, Object> personal = ApplicantIdentityResolver.canonicalisePersonalInfo(
                intakeMetadataEnricher.enrichPersonalInfo(request, customerId, actingUserId, actingUserRole));
        IntakeSegment segment = AnchorIntakeValidation.resolveSegment(request);
        ApplicationConfigurationAuthority.assertOrdinaryCreateDoesNotSelectWorkflow(
                request.getWorkflowId(), actingUserRole);
        UUID workflowId = null;
        if (request.getWorkflowId() != null) {
            workflowId = resolveWorkflowBinding(
                    request.getWorkflowId(), request.getBorrowerType(), request.getLoanProduct(), segment);
        }

        LoanApplication application = LoanApplication.builder()
                .applicationNumber(applicationNumber)
                .customerId(customerId)
                .borrowerType(request.getBorrowerType())
                .loanProduct(request.getLoanProduct())
                .intakeSegment(segment)
                .workflowId(workflowId)
                .requestedAmount(request.getRequestedAmount())
                .tenureMonths(request.getTenureMonths())
                .lmsProductCode(resolveApplicationLmsProductCode(request.getLoanProduct(), request.getLmsProductCode()))
                .lmsTenureUnit(resolveApplicationLmsTenureUnit(request.getLoanProduct(), request.getLmsTenureUnit()))
                .personalInfo(personal)
                .businessInfo(ApplicantIdentityResolver.canonicaliseBusinessInfo(request.getBusinessInfo()))
                .financialInfo(request.getFinancialInfo())
                .collateralInfo(request.getCollateralInfo() != null ? new HashMap<>(request.getCollateralInfo()) : null)
                .status(ApplicationStatus.DRAFT)
                .build();

        application = applicationRepository.save(application);
        if (workflowId != null) {
            WorkflowConfig cfg = workflowConfigRepository.findById(workflowId).orElseThrow();
            applicationWorkflowResolver.stampExplicit(application, cfg);
        }
        log.info("Application created: {} for customer: {}", applicationNumber, customerId);

        auditService.logEvent(application.getId(), "APPLICATION", "CREATED",
                actingUserId != null ? actingUserId : customerId, null,
                Map.of("applicationNumber", applicationNumber, "status", "DRAFT", "customerId", customerId.toString()),
                "Application created");

        return enrich(toResponse(application), application);
    }

    private static void validateCreateEmailForAnchor(CreateApplicationRequest request) {
        if (AnchorIntakeValidation.resolveSegment(request) != IntakeSegment.ANCHOR) {
            return;
        }
        Map<String, Object> businessInfo = request.getBusinessInfo() != null ? request.getBusinessInfo() : Map.of();
        String email = String.valueOf(businessInfo.getOrDefault("email", "")).trim();
        if (email.isBlank()) {
            throw new BusinessRuleException(
                    "Email is required for anchor onboarding",
                    "EMAIL_REQUIRED",
                    "CREATE_APPLICATION",
                    Map.of("field", "email"));
        }
        if (!EMAIL_RE.matcher(email).matches()) {
            throw new BusinessRuleException(
                    "Please enter a valid email address",
                    "EMAIL_INVALID",
                    "CREATE_APPLICATION",
                    Map.of("field", "email"));
        }
    }

    private static void validateCreateEmailForIndividual(CreateApplicationRequest request) {
        if (request.getBorrowerType() != BorrowerType.INDIVIDUAL) {
            return;
        }
        // Borrower email is collected on the "Basic Borrower Details" step, which can render
        // AFTER the application is first created (e.g. Invoice Discounting + BORROWER onboarding
        // creates a DRAFT at the Product & Request step so the next step can link a PLP program).
        // We therefore do not require email at CREATE; it is enforced when the borrower step is
        // saved (frontend `validateBorrowerStep`) and again at final submit (`submitApplication`).
        // Format is still validated here so an obviously malformed value is rejected as early as
        // possible.
        Map<String, Object> personalInfo = request.getPersonalInfo() != null ? request.getPersonalInfo() : Map.of();
        String email = String.valueOf(personalInfo.getOrDefault("email", "")).trim();
        if (email.isBlank()) {
            return;
        }
        if (!EMAIL_RE.matcher(email).matches()) {
            throw new BusinessRuleException(
                    "Please enter a valid email address",
                    "EMAIL_INVALID",
                    "CREATE_APPLICATION",
                    Map.of("field", "email"));
        }
    }

    private static void requireBorrowerIntakeIfDeclared(
            CreateApplicationRequest request, UUID actingUserId, String actingUserRole) {
        Map<String, Object> pi = request.getPersonalInfo() != null ? request.getPersonalInfo() : Map.of();
        Object im = pi.get("intakeMode");
        if (im == null) {
            return;
        }
        if (!"BORROWER_SELF_SERVICE".equalsIgnoreCase(String.valueOf(im).trim())) {
            return;
        }
        if (actingUserId == null || actingUserRole == null || !"BORROWER".equalsIgnoreCase(actingUserRole.trim())) {
            throw new BusinessRuleException(
                    "Borrower self-service applications require a signed-in borrower account.",
                    "AUTH_REQUIRED",
                    "CREATE_APPLICATION",
                    Map.of("intakeMode", "BORROWER_SELF_SERVICE"));
        }
    }

    @Override
    public ApplicationResponse getApplication(UUID applicationId) {
        LoanApplication app = findApplicationOrThrow(applicationId);
        return enrich(toResponse(app), app);
    }

    @Override
    public Page<ApplicationResponse> listApplications(
            ApplicationStatus status, String borrowerType, String intakeSegment, Pageable pageable) {
        IntakeSegment seg = null;
        if (intakeSegment != null && !intakeSegment.isBlank()) {
            seg = IntakeSegment.valueOf(intakeSegment.trim().toUpperCase());
        }
        BorrowerType bt = null;
        if (borrowerType != null && !borrowerType.isBlank()) {
            bt = BorrowerType.valueOf(borrowerType.toUpperCase());
        }

        if (status != null && bt != null && seg != null) {
            return applicationRepository.findByStatusAndBorrowerTypeAndIntakeSegment(status, bt, seg, pageable)
                    .map(this::toResponse);
        }
        if (status != null && seg != null) {
            return applicationRepository.findByStatusAndIntakeSegment(status, seg, pageable).map(this::toResponse);
        }
        if (bt != null && seg != null) {
            return applicationRepository.findByBorrowerTypeAndIntakeSegment(bt, seg, pageable).map(this::toResponse);
        }
        if (seg != null) {
            return applicationRepository.findByIntakeSegment(seg, pageable).map(this::toResponse);
        }
        if (status != null && bt != null) {
            return applicationRepository.findByStatusAndBorrowerType(status, bt, pageable).map(this::toResponse);
        } else if (status != null) {
            return applicationRepository.findByStatus(status, pageable).map(this::toResponse);
        } else if (bt != null) {
            return applicationRepository.findByBorrowerType(bt, pageable).map(this::toResponse);
        }
        return applicationRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public ApplicationResponse updateApplication(UUID applicationId, UpdateApplicationRequest request) {
        LoanApplication app = findApplicationOrThrow(applicationId);

        if (ApplicationStateMachine.isTerminal(app.getStatus())) {
            throw new BusinessRuleException(
                    "Cannot update application in terminal status: " + app.getStatus(),
                    "STATUS_TERMINAL",
                    "UPDATE_APPLICATION",
                    Map.of("status", app.getStatus().name())
            );
        }

        Map<String, Object> beforeSnapshot = applicationInputChangeTracker.fullFingerprint(app);

        if (request.getRequestedAmount() != null) app.setRequestedAmount(request.getRequestedAmount());
        if (request.getTenureMonths() != null) app.setTenureMonths(request.getTenureMonths());
        if (request.getLmsProductCode() != null && !isInvoiceDiscountingProduct(app.getLoanProduct())) {
            app.setLmsProductCode(blankToNull(request.getLmsProductCode()));
        }
        if (request.getLmsTenureUnit() != null && !isInvoiceDiscountingProduct(app.getLoanProduct())) {
            app.setLmsTenureUnit(blankToNull(request.getLmsTenureUnit()));
        }
        if (request.getWorkflowId() != null) {
            ApplicationConfigurationAuthority.assertWorkflowIdUpdateAllowed(app, request.getWorkflowId());
        }
        if (request.getPersonalInfo() != null) {
            app.setPersonalInfo(ApplicantIdentityResolver.canonicalisePersonalInfo(
                    mergeJsonb(app.getPersonalInfo(), request.getPersonalInfo())));
        }
        if (request.getBusinessInfo() != null) {
            app.setBusinessInfo(ApplicantIdentityResolver.canonicaliseBusinessInfo(
                    mergeJsonb(app.getBusinessInfo(), request.getBusinessInfo())));
        }
        if (request.getFinancialInfo() != null) app.setFinancialInfo(mergeJsonb(app.getFinancialInfo(), request.getFinancialInfo()));
        if (request.getCollateralInfo() != null) app.setCollateralInfo(mergeJsonb(app.getCollateralInfo(), request.getCollateralInfo()));
        if (request.getRemarks() != null) app.setRemarks(request.getRemarks());

        applicationInputChangeTracker.refreshKycChangeFlags(app);
        applicationInputChangeTracker.refreshIntakeChangeSinceSendBack(app);

        Map<String, Object> afterSnapshot = applicationInputChangeTracker.fullFingerprint(app);
        List<String> changedFields = applicationInputChangeTracker.diffSnapshots(beforeSnapshot, afterSnapshot);

        app = applicationRepository.save(app);
        log.info("Application updated: {}", app.getApplicationNumber());

        if (!changedFields.isEmpty()) {
            auditService.logEvent(
                    applicationId,
                    "APPLICATION",
                    "UPDATED",
                    null,
                    Map.of("fields", beforeSnapshot),
                    Map.of("fields", afterSnapshot, "changedFields", changedFields),
                    "Application fields updated: " + String.join(", ", changedFields));
            recordAuditService.capture(
                    "LOAN_APPLICATION",
                    applicationId.toString(),
                    "UPDATE",
                    app.getStatus() != null ? app.getStatus().name() : null,
                    null,
                    null,
                    applicationId,
                    beforeSnapshot,
                    afterSnapshot,
                    "Application fields updated");
        }

        return enrich(toResponse(app), app);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateIdentity(ValidateIdentityRequest request) {
        UUID selfId = request.applicationId();
        UUID customerId = null;
        // Co-applicant checks must not inherit the primary borrower's customerId, or duplicates
        // belonging to that primary on other apps would be incorrectly allowed.
        if (selfId != null && !Boolean.TRUE.equals(request.asCoApplicant())) {
            LoanApplication app = findApplicationOrThrow(selfId);
            customerId = app.getCustomerId();
        }
        applicationSubmitIdentityValidator.validateFields(
                selfId,
                customerId,
                request.email(),
                request.mobile(),
                request.panNumber(),
                request.gstin());
    }

    @Override
    @Transactional
    public ApplicationResponse transitionStatus(UUID applicationId, ApplicationStatus newStatus, String remarks) {
        LoanApplication app = findApplicationOrThrow(applicationId);
        ApplicationStatus oldStatus = app.getStatus();

        // Prevent bypassing business lifecycle orchestration via the generic transition endpoint.
        // Core stages must be advanced only through /api/v1/flow/* endpoints which enforce prerequisites.
        Set<ApplicationStatus> restrictedTargets = EnumSet.of(
                ApplicationStatus.KYC_IN_PROGRESS,
                ApplicationStatus.KYC_FAILED,
                ApplicationStatus.UNDERWRITING,
                ApplicationStatus.UNDERWRITING_COMPLETED,
                ApplicationStatus.APPROVED,
                ApplicationStatus.CAM_READY,
                ApplicationStatus.CAM_REVIEWED,
                ApplicationStatus.SANCTION_PENDING,
                ApplicationStatus.SANCTIONED,
                ApplicationStatus.KFS_GENERATED,
                ApplicationStatus.SANCTION_ISSUED,
                ApplicationStatus.ESIGN_PENDING,
                ApplicationStatus.ESIGN_COMPLETED,
                ApplicationStatus.READY_FOR_DISBURSEMENT,
                ApplicationStatus.DISBURSEMENT_PENDING,
                ApplicationStatus.DISBURSED
        );

        if (restrictedTargets.contains(newStatus)) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "GENERIC_TRANSITION_BLOCKED",
                    null,
                    Map.of("status", oldStatus.name(), "reason", "RESTRICTED_TARGET_STATUS", "action", "GENERIC_TRANSITION", "targetStatus", newStatus.name(), "remarks", remarks != null ? remarks : ""),
                    null,
                    "Direct transition to core lifecycle status is not allowed: " + newStatus);
            throw new BusinessRuleException(
                    "Direct transition to core lifecycle status is not allowed: " + newStatus + ". Use flow/orchestration endpoints.",
                    "RESTRICTED_TARGET_STATUS",
                    "GENERIC_TRANSITION",
                    Map.of("status", oldStatus.name(), "targetStatus", newStatus.name(), "remarks", remarks != null ? remarks : "")
            );
        }

        if (!ApplicationStateMachine.isValidTransition(oldStatus, newStatus)) {
            throw new BusinessRuleException(
                    String.format("Cannot transition from %s to %s. Allowed: %s",
                            oldStatus, newStatus, ApplicationStateMachine.getAllowedTransitions(oldStatus)),
                    "INVALID_STATE_TRANSITION",
                    "GENERIC_TRANSITION",
                    Map.of("status", oldStatus.name(), "targetStatus", newStatus.name(), "allowed", ApplicationStateMachine.getAllowedTransitions(oldStatus))
            );
        }

        app.setStatus(newStatus);
        if (remarks != null) app.setRemarks(remarks);
        if (newStatus == ApplicationStatus.CONSENT_PENDING || newStatus == ApplicationStatus.KYC_IN_PROGRESS) {
            if (app.getSubmittedAt() == null) app.setSubmittedAt(Instant.now());
        }

        StatusChangeContext.set(null, remarks != null ? remarks : "Status transition");
        try {
            app = applicationRepository.save(app);
        } finally {
            StatusChangeContext.clear();
        }
        log.info("Application {} transitioned: {} -> {}", app.getApplicationNumber(), oldStatus, newStatus);

        auditService.logEvent(applicationId, "STATUS_CHANGE", oldStatus + " -> " + newStatus,
                null, Map.of("status", oldStatus.name()),
                Map.of("status", newStatus.name()),
                remarks != null ? remarks : "Status transition");

        return enrich(toResponse(app), app);
    }

    @Override
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        List<Object[]> statusCounts = applicationRepository.countByStatusGrouped();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : statusCounts) {
            String status = ((ApplicationStatus) row[0]).name();
            Long count = (Long) row[1];
            byStatus.put(status, count);
            total += count;
        }
        summary.put("total", total);
        summary.put("byStatus", byStatus);

        long activeCount = total
                - applicationRepository.countByStatus(ApplicationStatus.DISBURSED)
                - applicationRepository.countByStatus(ApplicationStatus.REJECTED)
                - applicationRepository.countByStatus(ApplicationStatus.WITHDRAWN);
        summary.put("active", activeCount);
        summary.put("kycInProgress", applicationRepository.countByStatus(ApplicationStatus.KYC_IN_PROGRESS));
        summary.put("underwritingInProgress", applicationRepository.countByStatus(ApplicationStatus.UNDERWRITING));

        return summary;
    }

    private LoanApplication findApplicationOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private String generateApplicationNumber(BorrowerType borrowerType, IntakeSegment intakeSegment) {
        if (intakeSegment == IntakeSegment.ANCHOR) {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            long seq = SEQUENCE.incrementAndGet();
            return String.format("LOS-ANC-%s-%05d", date, seq % 100000);
        }
        String prefix = switch (borrowerType) {
            case INDIVIDUAL -> "IND";
            case PROPRIETOR -> "PRP";
            case PARTNERSHIP -> "PRT";
            case COMPANY -> "CMP";
        };
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = SEQUENCE.incrementAndGet();
        return String.format("LOS-%s-%s-%05d", prefix, date, seq % 100000);
    }

    private Map<String, Object> mergeJsonb(Map<String, Object> existing, Map<String, Object> updates) {
        if (existing == null) return new HashMap<>(updates);
        Map<String, Object> merged = new HashMap<>(existing);
        merged.putAll(updates);
        return merged;
    }

    private ApplicationResponse enrich(ApplicationResponse r, LoanApplication app) {
        r.setCreditControlView(creditControlService.buildReadView(app));
        underwritingEvaluationService.findLatest(app).ifPresent(ev ->
                r.setLatestUnderwritingEvaluation(underwritingEvaluationService.toApiMap(ev)));
        creditAppraisalMemoRepository.findByApplicationId(app.getId())
                .ifPresent(cam -> r.setCamStatus(cam.getCamStatus()));
        applicationInputChangeTracker.applyToResponse(r, app);
        return r;
    }

    private ApplicationResponse toResponse(LoanApplication app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .customerId(app.getCustomerId())
                .borrowerType(app.getBorrowerType())
                .loanProduct(app.getLoanProduct())
                .intakeSegment(app.getIntakeSegment())
                .workflowId(app.getWorkflowId())
                .workflowVersion(app.getWorkflowVersion())
                .workflowName(resolvePinnedWorkflowName(app))
                .workflowResolutionSource(app.getWorkflowResolutionSource())
                .selectedCustomerCategoryId(app.getSelectedCustomerCategoryId())
                .selectedCustomerCategoryCode(app.getSelectedCustomerCategoryCode())
                .selectedCustomerCategoryVersion(app.getSelectedCustomerCategoryVersion())
                .selectedCustomerCategoryDisplayName(resolveSelectedCategoryDisplayName(app))
                .selectedPolicyApplicabilityId(app.getSelectedPolicyApplicabilityId())
                .selectedPolicyDocumentId(app.getSelectedPolicyDocumentId())
                .categorySelectionState(app.getCategorySelectionState())
                .intakeOwner(app.getIntakeOwner())
                .intakeCompletedStep(app.getIntakeCompletedStep())
                .borrowerSentBackNotes(app.getBorrowerSentBackNotes())
                .requestedAmount(app.getRequestedAmount())
                .interestRate(app.getInterestRate())
                .tenureMonths(app.getTenureMonths())
                .lmsProductCode(app.getLmsProductCode())
                .lmsTenureUnit(app.getLmsTenureUnit())
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
                .subProgramId(app.getSubProgramId())
                .plpBorrowerId(app.getPlpBorrowerId())
                .plpSubProgramBorrowerId(app.getPlpSubProgramBorrowerId())
                .plpBorrowerProgramMappingId(app.getPlpBorrowerProgramMappingId())
                .plpProgramSyncStatus(app.getPlpProgramSyncStatus())
                .plpProgramSyncError(app.getPlpProgramSyncError())
                .plpProgramSyncedAt(app.getPlpProgramSyncedAt())
                .plpBorrowerSyncStatus(app.getPlpBorrowerSyncStatus())
                .plpBorrowerSyncedAt(app.getPlpBorrowerSyncedAt())
                .plpLinkSyncStatus(app.getPlpLinkSyncStatus())
                .plpLinkSyncedAt(app.getPlpLinkSyncedAt())
                .plpMappingSyncStatus(app.getPlpMappingSyncStatus())
                .plpMappingSyncedAt(app.getPlpMappingSyncedAt())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .submittedAt(app.getSubmittedAt())
                .build();
    }

    /** Exact workflow name for the pinned application.workflow_id — never latest/active catalog. */
    private String resolvePinnedWorkflowName(LoanApplication app) {
        if (app.getWorkflowId() == null) {
            return null;
        }
        return workflowConfigRepository.findById(app.getWorkflowId())
                .map(WorkflowConfig::getName)
                .orElse(null);
    }

    private String resolveSelectedCategoryDisplayName(LoanApplication app) {
        if (app.getSelectedCustomerCategoryId() == null) {
            return null;
        }
        return customerCategoryRepository.findById(app.getSelectedCustomerCategoryId())
                .map(CategoryPropositionConfig::customerFacingName)
                .orElse(app.getSelectedCustomerCategoryCode());
    }

    private UUID resolveWorkflowBinding(
            UUID workflowId, BorrowerType borrowerType, String loanProduct, IntakeSegment intakeSegment) {
        if (workflowId == null) {
            return null;
        }
        WorkflowConfig cfg = workflowConfigRepository.findById(workflowId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Workflow not found: " + workflowId,
                        "WORKFLOW_NOT_FOUND",
                        "BIND_WORKFLOW",
                        Map.of("workflowId", workflowId.toString())));
        IntakeSegment seg = intakeSegment != null ? intakeSegment : IntakeSegment.BORROWER;
        String expectedSeg = seg.name();
        String actualSeg = cfg.getIntakeSegment() == null || cfg.getIntakeSegment().isBlank()
                ? IntakeSegment.BORROWER.name()
                : cfg.getIntakeSegment().trim().toUpperCase(Locale.ROOT);
        if (!cfg.isActive()
                || borrowerType == null
                || !borrowerType.name().equals(cfg.getBorrowerType())
                || loanProduct == null
                || !loanProduct.equals(cfg.getLoanProduct())
                || !expectedSeg.equals(actualSeg)) {
            throw new BusinessRuleException(
                    "Selected workflow does not match this application borrower type, loan product, or intake segment",
                    "WORKFLOW_MISMATCH",
                    "BIND_WORKFLOW",
                    Map.of("workflowId", workflowId.toString()));
        }
        return workflowId;
    }

    private static boolean isInvoiceDiscountingProduct(String loanProduct) {
        return StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(loanProduct);
    }

    private static String resolveApplicationLmsProductCode(String loanProduct, String lmsProductCode) {
        if (isInvoiceDiscountingProduct(loanProduct)) {
            return null;
        }
        return blankToNull(lmsProductCode);
    }

    private static String resolveApplicationLmsTenureUnit(String loanProduct, String lmsTenureUnit) {
        if (isInvoiceDiscountingProduct(loanProduct)) {
            return null;
        }
        return blankToNull(lmsTenureUnit);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
