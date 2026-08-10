package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Human-readable policy preview for credit managers (not JSON DSL).
 */
@Component
public class PolicyPreviewService {

    public Map<String, Object> preview(PolicyStudioSession session) {
        List<String> lines = new ArrayList<>();
        List<String> products = session.getClauses().stream()
                .map(c -> c.getProductScope())
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .toList();
        for (String product : products) {
            lines.add(product);
            lines.add("");
            lines.add("Eligibility:");
            int n = 1;
            for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
                Object productsScope = rule.getScope() == null ? null : rule.getScope().get("products");
                if (productsScope instanceof List<?> list && !list.contains(product) && !list.contains("ALL")) {
                    continue;
                }
                lines.add(n++ + ". " + humanize(rule));
                if (rule.getLineage() != null && rule.getLineage().get("sourceText") != null) {
                    lines.add("   Source: " + session.getDocument().getName()
                            + ", clause " + rule.getLineage().getOrDefault("clauseNumber", ""));
                }
            }
            lines.add("");
            lines.add("Missing banking/bureau data:");
            lines.add("   DATA_INSUFFICIENT → REFER");
            lines.add("");
        }
        if (products.isEmpty()) {
            lines.add(session.getDocument().getName());
            lines.add("");
            lines.add("Rules:");
            int n = 1;
            for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
                lines.add(n++ + ". " + humanize(rule));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", session.getDocument().getName());
        out.put("text", String.join("\n", lines));
        out.put("products", products);
        out.put("ruleCount", session.getRuleCandidates().size());
        return out;
    }

    private String humanize(CiPolicyRuleCandidate rule) {
        String id = rule.getSystemRuleId();
        if (id == null) {
            return "Rule";
        }
        return switch (id) {
            case "BANK_STARTER_ADB_GTE_EDI" ->
                    "Average daily balance (3M) must be >= Proposed EDI.";
            case "BANK_DIGILEAP_ADB_DIV5_GTE_EDI" ->
                    "Adjusted average daily balance (3M) / 5 must be >= Proposed EDI.";
            case "BANK_DIGILEAP_TXN_GTE_20" ->
                    "Average monthly qualifying transactions (3M) must be >= 20.";
            case "BANK_SMART_SWITCH_SETTLEMENT_DIV10_GTE_EDI" ->
                    "QR settlement average / 10 must be >= Proposed EDI.";
            case "BANK_SMART_SWITCH_SETTLEMENT_COUNT_GTE_20" ->
                    "QR settlement monthly count must be >= 20.";
            case "BANK_REBOOST_ADB_DIV5_GTE_EDI_IF_AMT_GT_60000" ->
                    "If loan amount > 60000: ADB/5 >= Proposed EDI.";
            case "BANK_REBOOST_TXN_GTE_30_IF_AMT_GT_60000" ->
                    "If loan amount > 60000: average monthly transactions >= 30.";
            case "BANK_INWARD_RETURN_BRANCHED_100" ->
                    "Inward cheque return rule branched at exactly 100 (human boundary selection required).";
            case "BUREAU_SCORE_OR_NTC_OR_GTE_650" ->
                    "Bureau score NTC or >= 650.";
            case "BUREAU_DPD_LAST_6M" ->
                    "Max DPD in last 6 months must not exceed policy threshold.";
            case "BUREAU_INQUIRIES_CURRENT_MONTH" ->
                    "Current-month bureau inquiries within limit (EvaluationContext clock).";
            case "BUREAU_CC_OVERDUE_GT_5000" ->
                    "Credit-card overdue amount must not exceed 5000.";
            default -> id.replace('_', ' ');
        };
    }
}
