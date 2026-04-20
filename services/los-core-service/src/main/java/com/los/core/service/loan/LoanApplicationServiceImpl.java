package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationServiceImpl implements ILoanApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final AuditService auditService;

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    @Override
    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, UUID customerId) {
        String applicationNumber = generateApplicationNumber(request.getBorrowerType());

        LoanApplication application = LoanApplication.builder()
                .applicationNumber(applicationNumber)
                .customerId(customerId)
                .borrowerType(request.getBorrowerType())
                .loanProduct(request.getLoanProduct())
                .requestedAmount(request.getRequestedAmount())
                .tenureMonths(request.getTenureMonths())
                .personalInfo(request.getPersonalInfo())
                .businessInfo(request.getBusinessInfo())
                .financialInfo(request.getFinancialInfo())
                .status(ApplicationStatus.DRAFT)
                .build();

        application = applicationRepository.save(application);
        log.info("Application created: {} for customer: {}", applicationNumber, customerId);

        auditService.logEvent(application.getId(), "APPLICATION", "CREATED",
                customerId, null,
                Map.of("applicationNumber", applicationNumber, "status", "DRAFT"),
                "Application created");

        return toResponse(application);
    }

    @Override
    public ApplicationResponse getApplication(UUID applicationId) {
        LoanApplication app = findApplicationOrThrow(applicationId);
        return toResponse(app);
    }

    @Override
    public Page<ApplicationResponse> listApplications(ApplicationStatus status, String borrowerType, Pageable pageable) {
        if (status != null && borrowerType != null) {
            BorrowerType bt = BorrowerType.valueOf(borrowerType.toUpperCase());
            return applicationRepository.findByStatusAndBorrowerType(status, bt, pageable).map(this::toResponse);
        } else if (status != null) {
            return applicationRepository.findByStatus(status, pageable).map(this::toResponse);
        } else if (borrowerType != null) {
            BorrowerType bt = BorrowerType.valueOf(borrowerType.toUpperCase());
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

        if (request.getRequestedAmount() != null) app.setRequestedAmount(request.getRequestedAmount());
        if (request.getTenureMonths() != null) app.setTenureMonths(request.getTenureMonths());
        if (request.getPersonalInfo() != null) app.setPersonalInfo(mergeJsonb(app.getPersonalInfo(), request.getPersonalInfo()));
        if (request.getBusinessInfo() != null) app.setBusinessInfo(mergeJsonb(app.getBusinessInfo(), request.getBusinessInfo()));
        if (request.getFinancialInfo() != null) app.setFinancialInfo(mergeJsonb(app.getFinancialInfo(), request.getFinancialInfo()));
        if (request.getCollateralInfo() != null) app.setCollateralInfo(mergeJsonb(app.getCollateralInfo(), request.getCollateralInfo()));
        if (request.getRemarks() != null) app.setRemarks(request.getRemarks());

        app = applicationRepository.save(app);
        log.info("Application updated: {}", app.getApplicationNumber());

        return toResponse(app);
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
                ApplicationStatus.APPROVED,
                ApplicationStatus.SANCTION_ISSUED,
                ApplicationStatus.ESIGN_PENDING,
                ApplicationStatus.ESIGN_COMPLETED,
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

        app = applicationRepository.save(app);
        log.info("Application {} transitioned: {} -> {}", app.getApplicationNumber(), oldStatus, newStatus);

        auditService.logEvent(applicationId, "STATUS_CHANGE", oldStatus + " -> " + newStatus,
                null, Map.of("status", oldStatus.name()),
                Map.of("status", newStatus.name()),
                remarks != null ? remarks : "Status transition");

        return toResponse(app);
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

        return summary;
    }

    private LoanApplication findApplicationOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private String generateApplicationNumber(BorrowerType borrowerType) {
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
