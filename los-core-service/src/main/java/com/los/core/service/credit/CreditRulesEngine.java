package com.los.core.service.credit;

import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Configurable Credit Rules Engine — BR-4.4, BR-4.6.
 * Evaluates loan applications against configurable credit policies.
 * Rules can be loaded from DB or config; here we provide a rules-driven framework.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditRulesEngine {

    private final AuditService auditService;

    // Configurable thresholds (in production, load from DB/config)
    private static final Map<String, CreditPolicy> POLICIES = Map.of(
            "PERSONAL_LOAN", new CreditPolicy(700, BigDecimal.valueOf(50), BigDecimal.valueOf(5000000), 60, BigDecimal.valueOf(12)),
            "BUSINESS_LOAN", new CreditPolicy(650, BigDecimal.valueOf(60), BigDecimal.valueOf(50000000), 120, BigDecimal.valueOf(14)),
            "HOME_LOAN", new CreditPolicy(650, BigDecimal.valueOf(70), BigDecimal.valueOf(100000000), 360, BigDecimal.valueOf(9)),
            "VEHICLE_LOAN", new CreditPolicy(680, BigDecimal.valueOf(55), BigDecimal.valueOf(10000000), 84, BigDecimal.valueOf(11)),
            "MSME_LOAN", new CreditPolicy(600, BigDecimal.valueOf(65), BigDecimal.valueOf(20000000), 120, BigDecimal.valueOf(13)),
            "GOLD_LOAN", new CreditPolicy(0, BigDecimal.valueOf(80), BigDecimal.valueOf(5000000), 36, BigDecimal.valueOf(10)),
            "LAP", new CreditPolicy(650, BigDecimal.valueOf(65), BigDecimal.valueOf(50000000), 240, BigDecimal.valueOf(10)),
            "DEFAULT", new CreditPolicy(700, BigDecimal.valueOf(50), BigDecimal.valueOf(5000000), 60, BigDecimal.valueOf(14))
    );

    public record CreditPolicy(int minCreditScore, BigDecimal maxFoirPercent,
                                 BigDecimal maxLoanAmount, int maxTenureMonths,
                                 BigDecimal maxInterestRate) {}

    public record CreditEvaluation(
            boolean approved,
            String decision,
            int riskScore,
            String riskCategory,
            List<String> ruleResults,
            List<String> deviations,
            Map<String, Object> metrics
    ) {}

    /**
     * Evaluate a loan application against the credit policy rules.
     */
    public CreditEvaluation evaluate(UUID applicationId, String loanProduct,
                                      int creditScore, BigDecimal monthlyIncome,
                                      BigDecimal existingEmi, BigDecimal requestedAmount,
                                      int requestedTenure, Map<String, Object> additionalData) {

        CreditPolicy policy = POLICIES.getOrDefault(loanProduct.toUpperCase().replace(" ", "_"),
                POLICIES.get("DEFAULT"));

        List<String> ruleResults = new ArrayList<>();
        List<String> deviations = new ArrayList<>();
        int riskScore = 100; // Start at 100, deduct for failures
        boolean allPassed = true;

        // Rule 1: Credit Score Check
        if (creditScore >= policy.minCreditScore) {
            ruleResults.add("PASS: Credit score " + creditScore + " >= " + policy.minCreditScore);
        } else if (creditScore >= policy.minCreditScore - 50) {
            ruleResults.add("DEVIATION: Credit score " + creditScore + " below threshold " + policy.minCreditScore + " (within deviation)");
            deviations.add("Credit score below threshold by " + (policy.minCreditScore - creditScore) + " points");
            riskScore -= 15;
        } else {
            ruleResults.add("FAIL: Credit score " + creditScore + " < " + policy.minCreditScore);
            riskScore -= 30;
            allPassed = false;
        }

        // Rule 2: FOIR Check
        BigDecimal proposedEmi = calculateEmi(requestedAmount, policy.maxInterestRate, requestedTenure);
        BigDecimal totalEmi = existingEmi.add(proposedEmi);
        BigDecimal foir = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? totalEmi.divide(monthlyIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);

        if (foir.compareTo(policy.maxFoirPercent) <= 0) {
            ruleResults.add("PASS: FOIR " + foir.setScale(1, RoundingMode.HALF_UP) + "% <= " + policy.maxFoirPercent + "%");
        } else if (foir.compareTo(policy.maxFoirPercent.add(BigDecimal.TEN)) <= 0) {
            ruleResults.add("DEVIATION: FOIR " + foir.setScale(1, RoundingMode.HALF_UP) + "% exceeds threshold (within deviation)");
            deviations.add("FOIR exceeds threshold by " + foir.subtract(policy.maxFoirPercent).setScale(1, RoundingMode.HALF_UP) + "%");
            riskScore -= 10;
        } else {
            ruleResults.add("FAIL: FOIR " + foir.setScale(1, RoundingMode.HALF_UP) + "% > " + policy.maxFoirPercent.add(BigDecimal.TEN) + "%");
            riskScore -= 25;
            allPassed = false;
        }

        // Rule 3: Loan Amount Check
        if (requestedAmount.compareTo(policy.maxLoanAmount) <= 0) {
            ruleResults.add("PASS: Loan amount within limit");
        } else {
            ruleResults.add("FAIL: Loan amount " + requestedAmount + " exceeds max " + policy.maxLoanAmount);
            riskScore -= 20;
            allPassed = false;
        }

        // Rule 4: Tenure Check
        if (requestedTenure <= policy.maxTenureMonths) {
            ruleResults.add("PASS: Tenure " + requestedTenure + " months <= " + policy.maxTenureMonths);
        } else {
            ruleResults.add("FAIL: Tenure " + requestedTenure + " exceeds max " + policy.maxTenureMonths);
            riskScore -= 10;
            allPassed = false;
        }

        // Rule 5: DPD / Willful Defaulter Check
        boolean isWillfulDefaulter = additionalData != null
                && Boolean.TRUE.equals(additionalData.get("willfulDefaulter"));
        int maxDpd = additionalData != null && additionalData.containsKey("maxDpd")
                ? ((Number) additionalData.get("maxDpd")).intValue() : 0;

        if (isWillfulDefaulter) {
            ruleResults.add("FAIL: Willful defaulter — automatic rejection");
            riskScore = 0;
            allPassed = false;
        } else if (maxDpd > 90) {
            ruleResults.add("FAIL: Max DPD " + maxDpd + " > 90 days");
            riskScore -= 30;
            allPassed = false;
        } else if (maxDpd > 30) {
            ruleResults.add("DEVIATION: Max DPD " + maxDpd + " > 30 days");
            deviations.add("DPD history: " + maxDpd + " days");
            riskScore -= 15;
        } else {
            ruleResults.add("PASS: No significant DPD history");
        }

        // Rule 6: Bounce Check
        int bounceCount = additionalData != null && additionalData.containsKey("bounceCount6Months")
                ? ((Number) additionalData.get("bounceCount6Months")).intValue() : 0;
        if (bounceCount > 3) {
            ruleResults.add("FAIL: Bounce count " + bounceCount + " > 3 in last 6 months");
            riskScore -= 20;
            allPassed = false;
        } else if (bounceCount > 0) {
            ruleResults.add("DEVIATION: Bounce count " + bounceCount + " in last 6 months");
            deviations.add("Bounce count: " + bounceCount);
            riskScore -= 5;
        } else {
            ruleResults.add("PASS: No bounces in last 6 months");
        }

        riskScore = Math.max(0, Math.min(100, riskScore));

        String riskCategory;
        if (riskScore >= 80) riskCategory = "LOW";
        else if (riskScore >= 60) riskCategory = "MEDIUM";
        else if (riskScore >= 40) riskCategory = "HIGH";
        else riskCategory = "VERY_HIGH";

        boolean approved = allPassed || (riskScore >= 60 && deviations.size() <= 2);
        String decision = approved
                ? (deviations.isEmpty() ? "AUTO_APPROVED" : "APPROVED_WITH_DEVIATIONS")
                : "REJECTED";

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("creditScore", creditScore);
        metrics.put("foir", foir.setScale(2, RoundingMode.HALF_UP));
        metrics.put("proposedEmi", proposedEmi.setScale(2, RoundingMode.HALF_UP));
        metrics.put("totalEmi", totalEmi.setScale(2, RoundingMode.HALF_UP));
        metrics.put("requestedAmount", requestedAmount);
        metrics.put("maxDpd", maxDpd);
        metrics.put("bounceCount", bounceCount);
        metrics.put("policyApplied", loanProduct);

        CreditEvaluation evaluation = new CreditEvaluation(
                approved, decision, riskScore, riskCategory, ruleResults, deviations, metrics);

        auditService.logEvent(applicationId, "CREDIT_EVALUATION",
                Map.of("decision", decision, "riskScore", String.valueOf(riskScore), "riskCategory", riskCategory));

        log.info("Credit evaluation for {}: decision={}, riskScore={}, category={}",
                applicationId, decision, riskScore, riskCategory);
        return evaluation;
    }

    /**
     * Get available credit policies.
     */
    public Map<String, CreditPolicy> getPolicies() {
        return POLICIES;
    }

    private BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        if (tenureMonths <= 0 || principal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        double monthlyRate = annualRate.doubleValue() / 1200.0;
        double onePlusR = 1 + monthlyRate;
        double onePlusRPowN = Math.pow(onePlusR, tenureMonths);
        double emi = principal.doubleValue() * monthlyRate * onePlusRPowN / (onePlusRPowN - 1);
        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
    }
}
