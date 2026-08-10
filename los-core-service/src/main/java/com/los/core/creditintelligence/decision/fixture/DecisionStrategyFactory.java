package com.los.core.creditintelligence.decision.fixture;

import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.support.ContentHasher;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixture-only P2 validation decision strategy. Never customer policy truth.
 * Label: VALIDATION_FIXTURE_DECISION / P2_VALIDATION_STRATEGY_V1
 */
public final class DecisionStrategyFactory {

    public static final String STRATEGY_CODE = "P2_VALIDATION_STRATEGY_V1";
    public static final String LABEL = "VALIDATION_FIXTURE_DECISION";

    private static final ContentHasher HASHER = new ContentHasher();

    private DecisionStrategyFactory() {}

    public static CiDecisionStrategy p2ValidationStrategyV1(UUID tenantId) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("strategyCode", STRATEGY_CODE);
        content.put("validationFixture", true);
        content.put("label", LABEL);
        content.put("policyOutcomeMapping", Map.of(
                "KNOCKOUT_FAIL", "DECLINE",
                "HARD_FAIL", "DECLINE",
                "FAIL", "DECLINE",
                "REFER", "REFER",
                "DATA_INSUFFICIENT", "DATA_INSUFFICIENT",
                "PASS", "CONTINUE"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("turnoverPct", 0.20);
        params.put("turnoverSource", "TRIANGULATED");
        params.put("bankingMultiplier", 3.0);
        params.put("bankingMetric", "banking.avg_daily_balance_3m");
        params.put("allowedFoir", 0.50);
        params.put("minDscr", 1.25);
        params.put("maxLtv", 0.75);
        params.put("haircut", 0.10);
        params.put("policyCap", 900000);
        params.put("interestRateForFoirSizing", 0.14);
        params.put("amortizationVersion", "EMI_FLAT_V1");
        params.put("defaultTenureMonths", 18);
        content.put("limitStrategy", Map.of(
                "combine", "MIN",
                "methods", List.of(
                        "TURNOVER_LIMIT", "BANKING_CREDIT_LIMIT", "FOIR_LIMIT",
                        "DSCR_LIMIT", "COLLATERAL_LIMIT", "POLICY_CAP", "REQUESTED_AMOUNT"),
                "params", params));
        content.put("tenureStrategy", Map.of(
                "minMonths", 6,
                "maxMonths", 36,
                "defaultMonths", 18));
        content.put("pricingStrategy", Map.of(
                "baseRate", 0.125,
                "floor", 0.11,
                "cap", 0.24,
                "evidenceWeaknessCausesRefer", true,
                "components", List.of(
                        Map.of("code", "RISK_GRADE_PREMIUM",
                                "bpsByGrade", Map.of(
                                        "A", 50, "B", 125, "C", 200, "D", 300, "DATA_INSUFFICIENT", 0)),
                        Map.of("code", "UNSECURED_PREMIUM", "bps", 50),
                        Map.of("code", "TENURE_PREMIUM", "bpsPerYearOver12", 25))));
        content.put("collateralStrategy", Map.of(
                "required", false,
                "maxLtv", 0.75,
                "haircut", 0.10));
        content.put("conditionStrategy", Map.of(
                "autoFromDi", true,
                "autoFromReconConflict", true,
                "autoFromCollateralShortfall", true));
        content.put("covenantStrategy", Map.of(
                "defaults", List.of("MIN_DSCR", "GST_FILING_TIMELINESS")));
        content.put("authorityStrategy", Map.of(
                "bands", List.of(
                        Map.of("maxAmount", 500000,
                                "grades", List.of("A", "B"),
                                "maxDeviations", 0,
                                "level", "CREDIT_MANAGER_L1",
                                "role", "CREDIT_MANAGER",
                                "approverCount", 1),
                        Map.of("maxAmount", 2500000,
                                "level", "CREDIT_MANAGER_L2",
                                "role", "CREDIT_MANAGER",
                                "approverCount", 1),
                        Map.of("maxAmount", 999999999,
                                "level", "CREDIT_COMMITTEE",
                                "role", "CREDIT_COMMITTEE",
                                "approverCount", 2,
                                "makerCheckerRequired", true)),
                "exceptionEscalation", "CREDIT_COMMITTEE"));

        String hash = HASHER.hashMap(content);
        return CiDecisionStrategy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strategyCode(STRATEGY_CODE)
                .version("1")
                .status("SHADOW")
                .content(content)
                .contentHash(hash)
                .createdAt(Instant.parse("2024-06-15T00:00:00Z"))
                .createdBy("VALIDATION_FIXTURE")
                .publishedAt(Instant.parse("2024-06-15T00:00:00Z"))
                .publishedBy("VALIDATION_FIXTURE")
                .build();
    }
}
