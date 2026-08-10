package com.los.core.service.flow.step;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.loan.InvoiceDiscountingLosLoanGuard;
import com.los.lms.dto.LoanHandoverRequest;
import com.los.lms.dto.LoanHandoverResponse;
import com.los.lms.entity.WorkflowLmsProductMapping;
import com.los.lms.service.LmsApplicationConfigResolver;
import com.los.lms.service.LmsProgramResolver;
import com.los.lms.service.LmsService;
import com.los.lms.service.WorkflowLmsProductResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link FlowStepType#DISBURSE} — LMS handover and DISBURSED status.
 * <p>
 * Populates all bl-core parity fields from the LoanApplication, product mapping config,
 * and borrower/collateral metadata into the {@link LoanHandoverRequest}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisburseLmsStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final LmsService lmsService;
    private final WorkflowLmsProductResolver workflowLmsProductResolver;
    private final LmsProgramResolver lmsProgramResolver;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;
    private final AuditService auditService;
    private final InvoiceDiscountingLosLoanGuard invoiceDiscountingLosLoanGuard;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.DISBURSE.equals(stepType);
    }

    @Override
    @Transactional
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        if (invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "DISBURSEMENT_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "INVOICE_DISCOUNTING_BORROWER", "action", "DISBURSE"),
                    null,
                    "Disbursement blocked: invoice discounting borrower onboarding does not create a term loan");
            throw new BusinessRuleException(
                    "Invoice discounting borrower onboarding is complete after sanction and eSign — no term loan disbursement applies.",
                    "INVOICE_DISCOUNTING_NO_TERM_LOAN",
                    "DISBURSE",
                    Map.of("status", app.getStatus().name())
            );
        }
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

        String borrowerName = com.los.core.service.loan.ApplicationPartyResolver.resolveDisplayName(app);
        String loanProduct = app.getLoanProduct() != null ? app.getLoanProduct() : "";

        // Resolve partner code from context or application metadata
        String partnerCode = extractString(context, "partnerCode");
        if (partnerCode == null && app.getBusinessInfo() != null) {
            partnerCode = extractString(app.getBusinessInfo(), "partnerCode", "partner_code", "partnerId");
        }

        String encoreProductCode = lmsProgramResolver.resolveEncoreProductCode(app, loanProduct);
        String tenureUnit = lmsApplicationConfigResolver.resolveTenureUnit(app);
        log.info("[LMS-DISBURSE] Encore product code {} tenureUnit {} for DISBURSE step (app={}, loanProduct={})",
                encoreProductCode, tenureUnit, app.getApplicationNumber(), loanProduct);
        Optional<WorkflowLmsProductMapping> fullMapping = workflowLmsProductResolver.resolveFullMapping(
                partnerCode, app.getBorrowerType(), loanProduct);

        // Build handover request with all bl-core parity fields
        LoanHandoverRequest.LoanHandoverRequestBuilder builder = LoanHandoverRequest.builder()
                .applicationId(applicationId)
                .applicationNumber(app.getApplicationNumber())
                .borrowerName(borrowerName)
                .borrowerType(app.getBorrowerType().name())
                .loanProduct(loanProduct)
                .encorePartyOrCustomerId(app.getLmsEncoreCustomerId())
                .productCode(encoreProductCode)
                .partnerCode(partnerCode)
                .sanctionedAmount(disbursementAmount)
                .interestRate(rate)
                .tenureMonths(app.getTenureMonths())
                .numberOfInstallments(app.getTenureMonths())
                .tenureUnit(tenureUnit)
                .emiAmount(emiAmount)
                .borrowerDetails(com.los.core.service.loan.ApplicationPartyResolver.buildIntegrationPartyMap(app));

        // Populate from product mapping config when available
        fullMapping.ifPresent(m -> {
            if (m.getBranchSetCode() != null) {
                builder.encoreBranchCode(m.getBranchSetCode());
            }
            if (m.getPenalInterestRate() != null) {
                builder.penalInterestRate(m.getPenalInterestRate());
            }
            if (m.getMoratoriumType() != null) {
                builder.moratoriumType(m.getMoratoriumType());
            }
            if (m.getMoratoriumPeriodMagnitude() != null) {
                builder.moratoriumPeriodMagnitude(m.getMoratoriumPeriodMagnitude());
            }
            if (m.getMoratoriumPeriodUnit() != null) {
                builder.moratoriumPeriodUnit(m.getMoratoriumPeriodUnit());
            }
        });

        // Override from context if workflow step provides explicit values
        overrideFromContext(builder, context);

        // User ID for Encore transaction posting
        String userId = extractString(context, "userId", "user_id", "loggedInUserId");
        if (userId != null) {
            builder.userId(userId);
        }

        LoanHandoverRequest handoverRequest = builder.build();
        LoanHandoverResponse handoverResponse = lmsService.handoverLoan(handoverRequest);
        Map<String, Object> lmsResult = handoverResponseToMap(handoverResponse);

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

    private static String extractBorrowerName(LoanApplication app) {
        if (app.getPersonalInfo() == null) return "";
        Map<String, Object> pi = app.getPersonalInfo();
        Object name = pi.get("fullName");
        if (name == null) name = pi.get("name");
        if (name == null) {
            String first = String.valueOf(pi.getOrDefault("firstName", ""));
            String last = String.valueOf(pi.getOrDefault("lastName", ""));
            name = (first + " " + last).trim();
        }
        return name.toString();
    }

    private static void overrideFromContext(LoanHandoverRequest.LoanHandoverRequestBuilder builder,
                                             Map<String, Object> context) {
        if (context == null) return;
        String branchCode = extractString(context, "encoreBranchCode", "branchCode");
        if (branchCode != null) builder.encoreBranchCode(branchCode);

        String disbDate = extractString(context, "disbursementDate");
        if (disbDate != null) builder.disbursementDate(disbDate);

        String trancheId = extractString(context, "trancheId", "tranche_id");
        if (trancheId != null) builder.trancheId(trancheId);

        String colending = extractString(context, "colendingApplicable");
        if (colending != null) builder.colendingApplicable(colending);

        String colenderProduct = extractString(context, "colenderProductCode");
        if (colenderProduct != null) builder.colenderProductCode(colenderProduct);

        String colenderId = extractString(context, "colenderId");
        if (colenderId != null) builder.colenderId(colenderId);

        String colenderRatio = extractString(context, "colenderLendingRatio");
        if (colenderRatio != null) builder.colenderLendingRatio(colenderRatio);

        String colenderRate = extractString(context, "colenderNormalInterestRate");
        if (colenderRate != null) builder.colenderNormalInterestRate(colenderRate);
    }

    @SafeVarargs
    private static String extractString(Map<String, Object>... sources) {
        return null;
    }

    private static String extractString(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private static Map<String, Object> handoverResponseToMap(LoanHandoverResponse resp) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (resp.getHandoverId() != null) {
            m.put("handoverId", resp.getHandoverId().toString());
        }
        m.put("applicationNumber", resp.getApplicationNumber());
        m.put("lmsReferenceId", resp.getLmsReferenceId());
        m.put("status", resp.getStatus());
        m.put("message", resp.getMessage());
        if (resp.getFirstEmiDate() != null) {
            m.put("firstEmiDate", resp.getFirstEmiDate().toString());
        }
        m.put("emiAmount", resp.getEmiAmount());
        m.put("totalEmis", resp.getTotalEmis());
        return m;
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
