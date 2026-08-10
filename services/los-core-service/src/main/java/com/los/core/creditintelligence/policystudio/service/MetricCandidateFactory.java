package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class MetricCandidateFactory {

    public List<CiPolicyMetricCandidate> create(
            List<CiPolicyClause> clauses,
            PolicyClauseExtractor.FixtureKind kind) {
        List<CiPolicyMetricCandidate> out = new ArrayList<>();
        if (kind == PolicyClauseExtractor.FixtureKind.BANKING_BRE) {
            List<Object> exclusions = new ArrayList<>();
            UUID anchorClause = null;
            for (CiPolicyClause c : clauses) {
                if (!ClauseType.METRIC_ADJUSTMENT.name().equals(c.getClauseType())) {
                    continue;
                }
                if (anchorClause == null) {
                    anchorClause = c.getId();
                }
                String lower = c.getSourceText().toLowerCase(Locale.ROOT);
                if (lower.contains("loan")) {
                    exclusions.add(Map.of("category", "LOAN_DISBURSEMENT", "period", "TRAILING_3M",
                            "clauseId", c.getId().toString()));
                } else if (lower.contains("gaming")) {
                    exclusions.add(Map.of("category", "ONLINE_GAMING_CREDIT",
                            "clauseId", c.getId().toString()));
                } else if (lower.contains("bulk") || lower.contains("deposition")) {
                    exclusions.add(Map.of("rule", "amount > 10 * average_depositions_3m",
                            "clauseId", c.getId().toString(), "ambiguousDenominator", true));
                }
            }
            if (!exclusions.isEmpty() && anchorClause != null) {
                Map<String, Object> expr = new LinkedHashMap<>();
                expr.put("base", "banking.avg_daily_balance_3m");
                expr.put("exclusions", exclusions);
                out.add(CiPolicyMetricCandidate.builder()
                        .id(UUID.randomUUID())
                        .clauseId(anchorClause)
                        .metricName("BANK_POLICY_ADJUSTED_ADB")
                        .baseMetric("banking.avg_daily_balance_3m")
                        .expression(expr)
                        .exclusions(exclusions)
                        .period("TRAILING_3M")
                        .aggregation("AVERAGE_DAILY")
                        .dependencies(List.of("banking.avg_daily_balance_3m", "bank.transaction"))
                        .missingDataPolicy("DATA_INSUFFICIENT")
                        .candidateCanonicalCode("BANK_POLICY_ADJUSTED_ADB")
                        .systemMetricId("BANK_POLICY_ADJUSTED_ADB")
                        .confidence(new BigDecimal("0.8200"))
                        .reviewStatus(ReviewState.AI_DRAFTED.name())
                        .metadata(Map.of("doNotMutateGlobal", "banking.avg_daily_balance_3m",
                                "NEW_METRIC_CANDIDATE", true))
                        .build());
            }
            for (CiPolicyClause c : clauses) {
                String lower = c.getSourceText().toLowerCase(Locale.ROOT);
                if (lower.contains("settlement") && ClauseType.HARD_RULE.name().equals(c.getClauseType())) {
                    out.add(CiPolicyMetricCandidate.builder()
                            .id(UUID.randomUUID())
                            .clauseId(c.getId())
                            .metricName("banking.settlement.avg_daily_3m")
                            .baseMetric(null)
                            .expression(Map.of("op", "NEW_METRIC_CANDIDATE", "period", "TRAILING_3M"))
                            .period("TRAILING_3M")
                            .candidateCanonicalCode("banking.settlement.avg_daily_3m")
                            .systemMetricId("BANK_SETTLEMENT_ADB_3M")
                            .confidence(new BigDecimal("0.6000"))
                            .reviewStatus(ReviewState.AI_DRAFTED.name())
                            .metadata(Map.of("NEW_METRIC_CANDIDATE", true, "availability", "UNAVAILABLE"))
                            .build());
                    break;
                }
            }
        }
        if (kind == PolicyClauseExtractor.FixtureKind.BUREAU_BRE) {
            for (CiPolicyClause c : clauses) {
                if (c.getParentClauseId() == null) {
                    continue;
                }
                String code = switch (c.getClauseNumber() == null ? "" : c.getClauseNumber()) {
                    case "1" -> "bureau.overdue.age_months";
                    case "2" -> "bureau.credit_after_overdue.exists";
                    case "3" -> "bureau.credit_after_overdue.clean_history_months";
                    default -> null;
                };
                if (code == null) {
                    continue;
                }
                out.add(CiPolicyMetricCandidate.builder()
                        .id(UUID.randomUUID())
                        .clauseId(c.getId())
                        .metricName(code)
                        .expression(Map.of("op", "NEW_METRIC_CANDIDATE", "code", code))
                        .candidateCanonicalCode(code)
                        .systemMetricId(systemId(code))
                        .missingDataPolicy("DATA_INSUFFICIENT")
                        .confidence(new BigDecimal("0.6500"))
                        .reviewStatus(ReviewState.AI_DRAFTED.name())
                        .metadata(Map.of("NEW_METRIC_CANDIDATE", true,
                                "notEquivalentTo", "bureau.max_dpd_6m"))
                        .build());
            }
        }
        return out;
    }

    private String systemId(String code) {
        return code.toUpperCase(Locale.ROOT).replace('.', '_');
    }
}
