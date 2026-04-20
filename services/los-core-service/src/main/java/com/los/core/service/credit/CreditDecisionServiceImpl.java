package com.los.core.service.credit;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditDecisionServiceImpl implements ICreditDecisionService {

    private final LoanApplicationRepository applicationRepository;
    private final KycStepResultRepository kycStepResultRepository;
    private final AuditService auditService;

    private static final int MIN_CREDIT_SCORE = 650;
    private static final BigDecimal MAX_FOIR = new BigDecimal("0.50"); // Fixed Obligation to Income Ratio
    private static final BigDecimal MAX_LTV = new BigDecimal("0.80"); // Loan to Value

    @Override
    public CreditDecisionResult evaluate(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        if (app.getStatus() != ApplicationStatus.UNDERWRITING) {
            throw new BusinessRuleException("Application must be in UNDERWRITING status for credit evaluation. Current: " + app.getStatus());
        }

        List<String> reasons = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        int riskScore = 100; // Start at 100, deduct for risk factors

        // 1. Check bureau score
        Optional<KycStepResult> bureauResult = kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.BUREAU_PULL);

        int creditScore = 0;
        if (bureauResult.isPresent() && bureauResult.get().getOutcome() == StepOutcome.SUCCESS) {
            Map<String, Object> parsed = bureauResult.get().getParsedData();
            if (parsed != null) {
                creditScore = parsed.get("creditScore") instanceof Number n ? n.intValue() : 0;
                int overdueAccounts = parsed.get("overdueAccounts") instanceof Number n ? n.intValue() : 0;
                int dpd90Plus = parsed.get("dpd90Plus") instanceof Number n ? n.intValue() : 0;
                boolean willfulDefaulter = Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("willfulDefaulter", false)));

                if (creditScore < MIN_CREDIT_SCORE) {
                    reasons.add("Credit score " + creditScore + " below minimum threshold of " + MIN_CREDIT_SCORE);
                    riskScore -= 30;
                } else if (creditScore < 700) {
                    conditions.add("Credit score " + creditScore + " is marginal — higher interest rate may apply");
                    riskScore -= 10;
                }

                if (overdueAccounts > 0) {
                    reasons.add("Active overdue accounts found: " + overdueAccounts);
                    riskScore -= 15;
                }

                if (dpd90Plus > 0) {
                    reasons.add("DPD 90+ days found in credit history");
                    riskScore -= 25;
                }

                if (willfulDefaulter) {
                    reasons.add("Flagged as willful defaulter — automatic rejection");
                    riskScore = 0;
                }
            }
        } else {
            reasons.add("Bureau report not available or failed");
            riskScore -= 20;
        }

        // 2. Check KYC completion
        long successfulKyc = kycStepResultRepository.countByApplicationIdAndOutcome(applicationId, StepOutcome.SUCCESS);
        if (successfulKyc < 3) {
            conditions.add("Less than 3 KYC steps completed — additional verification may be required");
            riskScore -= 5;
        }

        // 3. Evaluate financial ratios from application data
        Map<String, Object> financialInfo = app.getFinancialInfo();
        if (financialInfo != null) {
            BigDecimal monthlyIncome = toBigDecimal(financialInfo.get("monthlyIncome"));
            BigDecimal existingEmi = toBigDecimal(financialInfo.get("existingEmi"));

            if (monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal proposedEmi = calculateEmi(app.getRequestedAmount(), app.getInterestRate(), app.getTenureMonths());
                BigDecimal totalObligations = existingEmi != null ? existingEmi.add(proposedEmi) : proposedEmi;
                BigDecimal foir = totalObligations.divide(monthlyIncome, 4, RoundingMode.HALF_UP);

                if (foir.compareTo(MAX_FOIR) > 0) {
                    reasons.add("FOIR " + foir.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                            + "% exceeds maximum " + MAX_FOIR.multiply(BigDecimal.valueOf(100)) + "%");
                    riskScore -= 20;
                }
            }
        }

        // 4. Determine decision
        riskScore = Math.max(0, Math.min(100, riskScore));
        String decision;
        BigDecimal recommendedRate = app.getInterestRate();

        if (riskScore >= 70 && reasons.isEmpty()) {
            decision = "APPROVED";
        } else if (riskScore >= 50 && !reasons.stream().anyMatch(r -> r.contains("automatic rejection"))) {
            decision = "APPROVED_WITH_CONDITIONS";
            if (recommendedRate != null) {
                recommendedRate = recommendedRate.add(BigDecimal.valueOf(1.5)); // risk premium
            }
        } else {
            decision = "REJECTED";
        }

        CreditDecisionResult result = new CreditDecisionResult(
                decision,
                riskScore,
                creditScore,
                reasons,
                conditions,
                app.getRequestedAmount(),
                recommendedRate
        );

        log.info("Credit decision for application {}: {} (risk score: {})", applicationId, decision, riskScore);

        auditService.logEvent(applicationId, "CREDIT_DECISION", decision,
                null, null,
                Map.of("decision", decision, "riskScore", riskScore, "creditScore", creditScore),
                "Credit evaluation completed");

        return result;
    }

    private BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, Integer tenureMonths) {
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

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
