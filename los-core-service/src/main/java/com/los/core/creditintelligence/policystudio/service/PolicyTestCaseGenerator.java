package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PolicyTestCaseGenerator {

    public List<CiPolicyTestCase> generate(List<CiPolicyRuleCandidate> rules) {
        List<CiPolicyTestCase> out = new ArrayList<>();
        for (CiPolicyRuleCandidate rule : rules) {
            out.addAll(forRule(rule));
        }
        return out;
    }

    private List<CiPolicyTestCase> forRule(CiPolicyRuleCandidate rule) {
        String id = rule.getSystemRuleId();
        return switch (id) {
            case "BUREAU_DPD_LAST_6M" -> dpdCases(rule);
            case "BUREAU_INQUIRIES_CURRENT_MONTH" -> inquiryCases(rule);
            case "BUREAU_CC_OVERDUE_GT_5000" -> ccOverdueCases(rule);
            case "BANK_STARTER_ADB_GTE_EDI" -> starterCases(rule);
            case "BANK_DIGILEAP_ADB_DIV5_GTE_EDI" -> digileapAdbCases(rule);
            case "BANK_DIGILEAP_TXN_GTE_20" -> digileapTxnCases(rule);
            case "BANK_REBOOST_ADB_DIV5_GTE_EDI_IF_AMT_GT_60000" -> reboostAmountCases(rule);
            case "BANK_REBOOST_TXN_GTE_30_IF_AMT_GT_60000" -> reboostTxnCases(rule);
            case "BUREAU_SCORE_OR_NTC_OR_GTE_650" -> scoreCases(rule);
            default -> genericThresholdCases(rule);
        };
    }

    private List<CiPolicyTestCase> dpdCases(CiPolicyRuleCandidate rule) {
        List<CiPolicyTestCase> t = new ArrayList<>();
        t.add(tc(rule, "DPD_29_PASS", Map.of(), Map.of("bureau.max_dpd_6m", 29), "PASS", true));
        t.add(tc(rule, "DPD_30_PASS", Map.of(), Map.of("bureau.max_dpd_6m", 30), "PASS", true));
        t.add(tc(rule, "DPD_31_FAIL", Map.of(), Map.of("bureau.max_dpd_6m", 31), "FAIL", true));
        t.add(tc(rule, "DPD_MISSING_DI", Map.of(), Map.of(), "DATA_INSUFFICIENT", false));
        Map<String, Object> outside = new LinkedHashMap<>();
        outside.put("bureau.max_dpd_6m", 31);
        outside.put("dpd_age_months", 8);
        outside.put("inside_6m_window", false);
        t.add(tc(rule, "DPD_31_EIGHT_MONTHS_AGO_PASS", Map.of(), outside, "PASS", true));
        return t;
    }

    private List<CiPolicyTestCase> inquiryCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "INQ_3_PASS", Map.of("evalClockMonth", "USE_EVALUATION_CONTEXT"),
                        Map.of("bureau.inquiries.current_month", 3), "PASS", true),
                tc(rule, "INQ_4_FAIL", Map.of("evalClockMonth", "USE_EVALUATION_CONTEXT"),
                        Map.of("bureau.inquiries.current_month", 4), "FAIL", true),
                tc(rule, "INQ_MISSING_DI", Map.of(), Map.of(), "DATA_INSUFFICIENT", false)
        );
    }

    private List<CiPolicyTestCase> ccOverdueCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "CC_OVERDUE_5000_PASS", Map.of(), Map.of("bureau.cc_overdue_amount", 5000), "PASS", true),
                tc(rule, "CC_OVERDUE_5001_FAIL", Map.of(), Map.of("bureau.cc_overdue_amount", 5001), "FAIL", true),
                tc(rule, "CC_OVERDUE_MISSING_DI", Map.of(), Map.of(), "DATA_INSUFFICIENT", false)
        );
    }

    private List<CiPolicyTestCase> starterCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "STARTER_ADB_EQ_EDI_PASS", Map.of("application.proposed_edi", 10000),
                        Map.of("banking.avg_daily_balance_3m", 10000), "PASS", true),
                tc(rule, "STARTER_ADB_BELOW_EDI_FAIL", Map.of("application.proposed_edi", 10000),
                        Map.of("banking.avg_daily_balance_3m", 9999), "FAIL", true),
                tc(rule, "STARTER_ADB_ABOVE_EDI_PASS", Map.of("application.proposed_edi", 10000),
                        Map.of("banking.avg_daily_balance_3m", 10001), "PASS", false),
                tc(rule, "STARTER_MISSING_DI", Map.of(), Map.of(), "DATA_INSUFFICIENT", false)
        );
    }

    private List<CiPolicyTestCase> digileapAdbCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "DIGILEAP_ADB_DIV5_EQ_EDI_PASS", Map.of("application.proposed_edi", 2000),
                        Map.of("banking.avg_daily_balance_3m", 10000), "PASS", true),
                tc(rule, "DIGILEAP_ADB_DIV5_BELOW_FAIL", Map.of("application.proposed_edi", 2000),
                        Map.of("banking.avg_daily_balance_3m", 9999), "FAIL", true)
        );
    }

    private List<CiPolicyTestCase> digileapTxnCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "DIGILEAP_TXN_20_PASS", Map.of(),
                        Map.of("banking.transaction_count.average_monthly_3m", 20), "PASS", true),
                tc(rule, "DIGILEAP_TXN_19_FAIL", Map.of(),
                        Map.of("banking.transaction_count.average_monthly_3m", 19), "FAIL", true),
                tc(rule, "DIGILEAP_TXN_21_PASS", Map.of(),
                        Map.of("banking.transaction_count.average_monthly_3m", 21), "PASS", false)
        );
    }

    private List<CiPolicyTestCase> reboostAmountCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "REBOOST_AMT_60000_SCOPE_INACTIVE",
                        Map.of("application.loan_amount", 60000, "application.proposed_edi", 1000),
                        Map.of("banking.avg_daily_balance_3m", 1000), "PASS", true),
                tc(rule, "REBOOST_AMT_60001_SCOPE_ACTIVE_PASS",
                        Map.of("application.loan_amount", 60001, "application.proposed_edi", 1000),
                        Map.of("banking.avg_daily_balance_3m", 5000), "PASS", true),
                tc(rule, "REBOOST_AMT_60001_SCOPE_ACTIVE_FAIL",
                        Map.of("application.loan_amount", 60001, "application.proposed_edi", 1000),
                        Map.of("banking.avg_daily_balance_3m", 4000), "FAIL", true)
        );
    }

    private List<CiPolicyTestCase> reboostTxnCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "REBOOST_TXN_30_AT_60001_PASS",
                        Map.of("application.loan_amount", 60001),
                        Map.of("banking.transaction_count.average_monthly_3m", 30), "PASS", true),
                tc(rule, "REBOOST_TXN_29_AT_60001_FAIL",
                        Map.of("application.loan_amount", 60001),
                        Map.of("banking.transaction_count.average_monthly_3m", 29), "FAIL", true)
        );
    }

    private List<CiPolicyTestCase> scoreCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, "SCORE_MINUS1_PASS", Map.of(), Map.of("bureau.score", -1), "PASS", true),
                tc(rule, "SCORE_NTC_PASS", Map.of("bureau.status_ntc", true), Map.of(), "PASS", true),
                tc(rule, "SCORE_650_PASS", Map.of(), Map.of("bureau.score", 650), "PASS", true),
                tc(rule, "SCORE_649_FAIL", Map.of("bureau.status_ntc", false), Map.of("bureau.score", 649), "FAIL", true)
        );
    }

    private List<CiPolicyTestCase> genericThresholdCases(CiPolicyRuleCandidate rule) {
        return List.of(
                tc(rule, rule.getSystemRuleId() + "_MISSING", Map.of(), Map.of(), "DATA_INSUFFICIENT", false)
        );
    }

    private CiPolicyTestCase tc(CiPolicyRuleCandidate rule, String name,
                                Map<String, Object> facts, Map<String, Object> metrics,
                                String outcome, boolean boundary) {
        return CiPolicyTestCase.builder()
                .id(UUID.randomUUID())
                .clauseId(rule.getClauseId())
                .ruleCandidateId(rule.getId())
                .name(name)
                .inputFacts(facts)
                .inputMetrics(metrics)
                .expectedOutcome(outcome)
                .boundaryCase(boundary)
                .generatedBy("SYSTEM")
                .generationConfidence(new BigDecimal("0.9000"))
                .reviewStatus(ReviewState.AI_DRAFTED.name())
                .metadata(Map.of("systemRuleId", rule.getSystemRuleId()))
                .build();
    }
}
