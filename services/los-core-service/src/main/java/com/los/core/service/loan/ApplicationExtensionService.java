package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BR-2.9: Application cloning/renewal.
 * BR-2.10: Bulk application processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationExtensionService {

    private final LoanApplicationRepository applicationRepository;
    private final AuditService auditService;

    private static final AtomicLong CLONE_SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    /**
     * BR-2.9: Clone an existing application (for renewal or re-application).
     */
    @Transactional
    public LoanApplication cloneApplication(UUID sourceApplicationId) {
        LoanApplication source = applicationRepository.findById(sourceApplicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + sourceApplicationId));

        String suffix = source.getBorrowerType().name().substring(0, 3);
        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String cloneNumber = String.format("LOS-%s-%s-%05d", suffix, dateStr, CLONE_SEQ.incrementAndGet());

        LoanApplication clone = LoanApplication.builder()
                .applicationNumber(cloneNumber)
                .customerId(source.getCustomerId())
                .borrowerType(source.getBorrowerType())
                .loanProduct(source.getLoanProduct())
                .requestedAmount(source.getRequestedAmount())
                .interestRate(source.getInterestRate())
                .tenureMonths(source.getTenureMonths())
                .lmsProductCode(source.getLmsProductCode())
                .lmsTenureUnit(source.getLmsTenureUnit())
                .personalInfo(source.getPersonalInfo() != null ? new HashMap<>(source.getPersonalInfo()) : null)
                .businessInfo(source.getBusinessInfo() != null ? new HashMap<>(source.getBusinessInfo()) : null)
                .financialInfo(source.getFinancialInfo() != null ? new HashMap<>(source.getFinancialInfo()) : null)
                .collateralInfo(source.getCollateralInfo() != null ? new HashMap<>(source.getCollateralInfo()) : null)
                .remarks("Cloned from " + source.getApplicationNumber())
                .status(ApplicationStatus.DRAFT)
                .build();

        clone = applicationRepository.save(clone);

        auditService.logEvent(clone.getId(), "APPLICATION_CLONED",
                Map.of("sourceApplicationId", sourceApplicationId.toString(),
                       "sourceApplicationNumber", source.getApplicationNumber()));

        log.info("Application cloned: {} -> {}", source.getApplicationNumber(), clone.getApplicationNumber());
        return clone;
    }

    /**
     * BR-2.10: Bulk status transition for multiple applications.
     */
    @Transactional
    public Map<String, Object> bulkTransition(List<UUID> applicationIds, ApplicationStatus newStatus, String remarks) {
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

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

        for (UUID appId : applicationIds) {
            try {
                LoanApplication app = applicationRepository.findById(appId).orElse(null);
                if (app == null) {
                    errors.add(appId + ": not found");
                    failed++;
                    continue;
                }

                if (restrictedTargets.contains(newStatus)) {
                    auditService.logEvent(appId, "PREREQUISITE_BLOCK", "BULK_TRANSITION_BLOCKED",
                            null,
                            Map.of("status", app.getStatus().name(), "reason", "RESTRICTED_TARGET_STATUS", "action", "BULK_TRANSITION", "targetStatus", newStatus.name(), "remarks", remarks != null ? remarks : ""),
                            null,
                            "Bulk transition blocked: direct transition to core lifecycle status is not allowed: " + newStatus);
                    errors.add(appId + ": direct transition to core lifecycle status is not allowed: " + newStatus);
                    failed++;
                    continue;
                }

                app.setStatus(newStatus);
                if (remarks != null) {
                    app.setRemarks(remarks);
                }
                applicationRepository.save(app);
                auditService.logEvent(appId, "BULK_STATUS_TRANSITION",
                        Map.of("newStatus", newStatus.name(), "remarks", remarks != null ? remarks : ""));
                success++;
            } catch (Exception e) {
                errors.add(appId + ": " + e.getMessage());
                failed++;
            }
        }

        log.info("Bulk transition to {}: {} success, {} failed", newStatus, success, failed);

        return Map.of(
                "totalRequested", applicationIds.size(),
                "success", success,
                "failed", failed,
                "errors", errors,
                "targetStatus", newStatus.name()
        );
    }

    /**
     * BR-2.10: Bulk assign applications to an officer.
     */
    @Transactional
    public Map<String, Object> bulkAssign(List<UUID> applicationIds, UUID assigneeId) {
        int count = 0;
        for (UUID appId : applicationIds) {
            LoanApplication app = applicationRepository.findById(appId).orElse(null);
            if (app != null) {
                app.setAssignedTo(assigneeId);
                applicationRepository.save(app);
                count++;
            }
        }

        log.info("Bulk assigned {} applications to {}", count, assigneeId);
        return Map.of("assigned", count, "assigneeId", assigneeId.toString());
    }
}
