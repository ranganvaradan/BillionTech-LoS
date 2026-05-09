package com.los.core.service.flow.step;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.integration.LmsAdapterClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FlowStepType#DISBURSE} — LMS handover and DISBURSED status (same as legacy flow).
 * <p>
 * {@link com.los.core.service.transaction.ITransactionService#triggerDisbursement} is not used here
 * because it requires {@link ApplicationStatus#DISBURSEMENT_PENDING} and a different code path; keeping
 * this executor aligned with the previous {@code LoanApplicationFlowService#disburseAndHandoverToLms} behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisburseLmsStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final LmsAdapterClient lmsAdapterClient;
    private final AuditService auditService;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.DISBURSE.equals(stepType);
    }

    @Override
    @Transactional
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        if (app.getStatus() != ApplicationStatus.READY_FOR_DISBURSEMENT
                && app.getStatus() != ApplicationStatus.DISBURSEMENT_PENDING
                && app.getStatus() != ApplicationStatus.ESIGN_COMPLETED) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "DISBURSEMENT_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "NOT_READY_FOR_DISBURSE", "action", "DISBURSE"),
                    null,
                    "Disbursement blocked: requires READY_FOR_DISBURSEMENT (or legacy DISBURSEMENT_PENDING/ESIGN_COMPLETED)");
            throw new BusinessRuleException(
                    "Cannot disburse — application must be in READY_FOR_DISBURSEMENT. Current: " + app.getStatus(),
                    "NOT_READY_FOR_DISBURSE",
                    "DISBURSE",
                    Map.of("status", app.getStatus().name())
            );
        }

        BigDecimal disbursementAmount = app.getSanctionedAmount() != null
                ? app.getSanctionedAmount() : app.getRequestedAmount();
        BigDecimal rate = app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        BigDecimal emiAmount = calculateEmi(disbursementAmount, rate, app.getTenureMonths());

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

        Map<String, Object> lmsResult = lmsAdapterClient.handoverLoan(
                applicationId, app.getApplicationNumber(),
                borrowerName, app.getBorrowerType().name(),
                app.getLoanProduct(), disbursementAmount,
                rate, app.getTenureMonths(), emiAmount,
                app.getPersonalInfo()
        );

        String lmsStatus = String.valueOf(lmsResult.getOrDefault("status", "UNKNOWN"));
        String lmsReferenceId = String.valueOf(lmsResult.getOrDefault("lmsReferenceId", ""));

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
        return StepResult.ok(response);
    }

    private static BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, Integer tenureMonths) {
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
}
