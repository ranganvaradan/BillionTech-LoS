package com.los.core.creditintelligence.decisionpolicy.sim;

import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * KYC-7 calibrated VALIDATION FIXTURE cases (not real borrower data).
 */
public final class Kyc7FrozenCaseCatalog {

    private Kyc7FrozenCaseCatalog() {}

    public static List<FrozenDecisionSimulationCase> all(UUID tenantId) {
        List<FrozenDecisionSimulationCase> list = new ArrayList<>();
        list.add(cleanFullApproval());
        list.add(kycConclusiveFailure());
        list.add(kycManualRefer());
        list.add(kycMissingInformation());
        list.add(providerOutage());
        list.add(companyCinGstPass());
        list.add(vkycRequiredComplete());
        list.add(vkycRequiredMissing());
        list.add(creditHardDecline());
        list.add(creditRefer());
        list.add(strongCreditFullAmount());
        list.add(capacityCounterOffer());
        list.add(pricingAdjustment());
        list.add(collateralCondition());
        list.add(missingCreditDataAfterKycPass());
        return list;
    }

    public static FrozenDecisionSimulationCase byCode(String code) {
        return all(UUID.fromString("00000000-0000-0000-0000-000000000001")).stream()
                .filter(c -> c.caseCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown fixture: " + code));
    }

    private static FrozenDecisionSimulationCase cleanFullApproval() {
        return base("CLEAN_FULL_APPROVAL", "Clean full approval path",
                "KYC PASS → Credit PASS → score → APPROVE",
                new BigDecimal("500000"), 18,
                List.of(ShadowKycPolicyEvaluationService.panPass()),
                individual(500_000),
                strongMetrics(780, 200_000),
                "PASS", "PASS");
    }

    private static FrozenDecisionSimulationCase kycConclusiveFailure() {
        return base("KYC_CONCLUSIVE_FAILURE", "KYC conclusive PAN failure",
                "KYC FAIL → Credit NOT RUN → DECLINE",
                new BigDecimal("300000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panFail()),
                individual(300_000),
                strongMetrics(720, 80_000),
                "FAIL", "N/A");
    }

    private static FrozenDecisionSimulationCase kycManualRefer() {
        return base("KYC_MANUAL_REFER", "KYC name-match manual refer",
                "KYC REFER → Credit NOT RUN → REFER",
                new BigDecimal("300000"), 24,
                List.of(new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.SUCCESS, null,
                        Map.of("nameMatch", false), false, false)),
                individual(300_000),
                strongMetrics(720, 80_000),
                "PASS", "N/A");
    }

    private static FrozenDecisionSimulationCase kycMissingInformation() {
        return base("KYC_MISSING_INFORMATION", "KYC missing mandatory PAN",
                "KYC MISSING → Credit NOT RUN → DATA INSUFFICIENT",
                new BigDecimal("200000"), 12,
                List.of(),
                individual(200_000),
                strongMetrics(700, 50_000),
                "INCOMPLETE", "N/A");
    }

    private static FrozenDecisionSimulationCase providerOutage() {
        return base("PROVIDER_OUTAGE", "PAN provider outage",
                "Technical outage → MISSING INFORMATION (not borrower FAIL)",
                new BigDecimal("250000"), 18,
                List.of(ShadowKycPolicyEvaluationService.panProviderOutage()),
                individual(250_000),
                strongMetrics(710, 60_000),
                "FAIL", "N/A");
    }

    private static FrozenDecisionSimulationCase companyCinGstPass() {
        return base("COMPANY_CIN_GST_PASS", "Company CIN/GST verified",
                "Company KYC PASS with CIN+GST",
                new BigDecimal("400000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass(),
                        ShadowKycPolicyEvaluationService.step(KycStepType.CIN_MCA21, StepOutcome.SUCCESS, null, Map.of(), false),
                        ShadowKycPolicyEvaluationService.step(KycStepType.GSTIN_VERIFY, StepOutcome.SUCCESS, null, Map.of(), false)),
                Map.of("requested_amount", 400_000, "borrower_type", "COMPANY"),
                strongMetrics(740, 120_000),
                "PASS", "PASS");
    }

    private static FrozenDecisionSimulationCase vkycRequiredComplete() {
        return base("VKYC_REQUIRED_COMPLETE", "VKYC required and complete",
                "Amount >5L with VKYC completed",
                new BigDecimal("600000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass(),
                        new NormalizedKycFactBuilder.StepEvidence(
                                KycStepType.VIDEO_KYC, StepOutcome.SUCCESS, null, Map.of(), false, null)),
                individual(600_000),
                strongMetrics(760, 150_000),
                "PASS", "PASS");
    }

    private static FrozenDecisionSimulationCase vkycRequiredMissing() {
        return base("VKYC_REQUIRED_MISSING", "VKYC required and missing",
                "Amount >5L without VKYC → KYC FAIL gate",
                new BigDecimal("600000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass()),
                individual(600_000),
                strongMetrics(760, 150_000),
                "PASS", "N/A");
    }

    private static FrozenDecisionSimulationCase creditHardDecline() {
        return base("CREDIT_HARD_DECLINE", "Credit knockout bureau score",
                "KYC PASS → Credit FAIL → DECLINE",
                new BigDecimal("400000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass()),
                individual(400_000),
                weakBureauMetrics(520, 80_000),
                "PASS", "FAIL");
    }

    private static FrozenDecisionSimulationCase creditRefer() {
        Map<String, Object> metrics = strongMetrics(700, 80_000);
        Map<String, Object> m = new LinkedHashMap<>(metrics);
        m.put("recon.gst_bank_turnover_variance_pct", 40);
        return base("CREDIT_REFER", "Credit soft refer on GST/bank variance",
                "KYC PASS → Credit REFER",
                new BigDecimal("400000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass()),
                individual(400_000),
                m,
                "PASS", "REFER");
    }

    private static FrozenDecisionSimulationCase strongCreditFullAmount() {
        return base("STRONG_CREDIT_FULL_AMOUNT", "Strong credit / full requested",
                "High capacity — recommended ≈ requested",
                new BigDecimal("500000"), 18,
                List.of(ShadowKycPolicyEvaluationService.panPass()),
                individual(500_000),
                strongMetrics(800, 500_000),
                "PASS", "PASS");
    }

    private static FrozenDecisionSimulationCase capacityCounterOffer() {
        // Requested 10L / 24m; capacity constrained via ADB + policy cap → COUNTER_OFFER
        return base("CAPACITY_COUNTER_OFFER", "Capacity constrained counter-offer",
                "Requested ₹10L/24m → eligible lower → COUNTER OFFER",
                new BigDecimal("1000000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass(),
                        new NormalizedKycFactBuilder.StepEvidence(
                                KycStepType.VIDEO_KYC, StepOutcome.SUCCESS, null, Map.of(), false, null)),
                individual(1_000_000),
                capacityConstrainedMetrics(),
                "PASS", "PASS");
    }

    private static FrozenDecisionSimulationCase pricingAdjustment() {
        return base("PRICING_ADJUSTMENT", "Pricing band adjustment",
                "Mid score → rate premium vs base",
                new BigDecimal("450000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass()),
                individual(450_000),
                strongMetrics(680, 100_000),
                "PASS", "PASS");
    }

    private static FrozenDecisionSimulationCase collateralCondition() {
        Map<String, Object> metrics = strongMetrics(720, 90_000);
        Map<String, Object> m = new LinkedHashMap<>(metrics);
        m.put("collateral.market_value", 200_000);
        m.put("collateral.required", true);
        return base("COLLATERAL_SHORTFALL", "Collateral shortfall / condition",
                "Collateral shortfall produces condition",
                new BigDecimal("700000"), 24,
                List.of(ShadowKycPolicyEvaluationService.panPass(),
                        new NormalizedKycFactBuilder.StepEvidence(
                                KycStepType.VIDEO_KYC, StepOutcome.SUCCESS, null, Map.of(), false, null)),
                individual(700_000),
                m,
                "PASS", "PASS");
    }

    private static FrozenDecisionSimulationCase missingCreditDataAfterKycPass() {
        return base("MISSING_CREDIT_DATA", "Missing credit data after KYC PASS",
                "KYC PASS → Credit DI → DATA INSUFFICIENT",
                new BigDecimal("350000"), 18,
                List.of(ShadowKycPolicyEvaluationService.panPass()),
                individual(350_000),
                Map.of(), // no bureau/banking metrics
                "PASS", "DATA_INSUFFICIENT");
    }

    private static FrozenDecisionSimulationCase base(
            String code, String display, String scenario,
            BigDecimal amount, int tenure,
            List<NormalizedKycFactBuilder.StepEvidence> steps,
            Map<String, Object> appFields,
            Map<String, Object> metrics,
            String legacyKyc,
            String legacyCredit
    ) {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("panPresent", true);
        if ("COMPANY".equals(String.valueOf(appFields.get("borrower_type")))) {
            hints.put("gstinPresent", true);
            hints.put("cinPresent", true);
        }
        return new FrozenDecisionSimulationCase(
                code, display, scenario, "TERM_LOAN",
                null, null, amount, tenure,
                steps, appFields, hints,
                Map.of(), metrics, Map.of(), Map.of(),
                legacyKyc, legacyCredit);
    }

    private static Map<String, Object> individual(int amount) {
        return Map.of("requested_amount", amount, "borrower_type", "INDIVIDUAL");
    }

    private static Map<String, Object> strongMetrics(int score, int adb) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bureau.score", score);
        m.put("bureau.ntc", false);
        m.put("bureau.writeoff.non_cc_count", 0);
        m.put("banking.avg_daily_balance_3m", adb);
        m.put("banking.avg_monthly_txn_count_3m", 40);
        m.put("recon.gst_bank_turnover_variance_pct", 5);
        m.put("turnover.annual", adb * 12L);
        m.put("income.monthly", Math.max(adb, 80_000));
        m.put("obligations.monthly", 10_000);
        m.put("foir.current", 0.25);
        m.put("dscr", 1.8);
        return m;
    }

    private static Map<String, Object> weakBureauMetrics(int score, int adb) {
        Map<String, Object> m = strongMetrics(score, adb);
        m.put("bureau.score", score);
        m.put("bureau.ntc", false);
        return m;
    }

    private static Map<String, Object> capacityConstrainedMetrics() {
        Map<String, Object> m = strongMetrics(734, 35_000);
        // Lower ADB / turnover so limit methods size below ₹10L; policy cap 9L also binds
        m.put("turnover.annual", 2_400_000);
        m.put("income.monthly", 60_000);
        m.put("obligations.monthly", 25_000);
        m.put("foir.current", 0.42);
        m.put("dscr", 1.3);
        m.put("bank.abb.average", 35_000);
        return m;
    }
}
