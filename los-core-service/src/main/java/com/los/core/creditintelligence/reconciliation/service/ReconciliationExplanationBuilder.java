package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.reconciliation.domain.ExplanationCode;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic free-text from explanation codes. Separates FACT / DETERMINISTIC_EXPLANATION / PROBABLE_CAUSE.
 */
@Component
public class ReconciliationExplanationBuilder {

    public record ExplanationBundle(
            String freeText,
            List<String> explanationCodes,
            List<Map<String, Object>> facts,
            List<Map<String, Object>> deterministicExplanations,
            List<Map<String, Object>> probableCauses) {
    }

    public ExplanationBundle build(
            String reconciliationCode,
            ReconciliationOutcome outcome,
            BigDecimal left,
            BigDecimal right,
            BigDecimal percentageVariance,
            List<ExplanationCode> codes) {

        List<ExplanationCode> codeList = codes != null ? codes : List.of();
        List<String> codeNames = codeList.stream().map(Enum::name).toList();

        List<Map<String, Object>> facts = new ArrayList<>();
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("kind", "FACT");
        fact.put("reconciliationCode", reconciliationCode);
        fact.put("left", left);
        fact.put("right", right);
        fact.put("percentageVariance", percentageVariance);
        fact.put("outcome", outcome != null ? outcome.name() : null);
        facts.add(fact);

        List<Map<String, Object>> deterministic = new ArrayList<>();
        List<Map<String, Object>> probable = new ArrayList<>();
        List<String> sentences = new ArrayList<>();

        for (ExplanationCode code : codeList) {
            String text = textFor(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code.name());
            row.put("text", text);
            if (isProbable(code)) {
                row.put("kind", "PROBABLE_CAUSE");
                probable.add(row);
            } else {
                row.put("kind", "DETERMINISTIC_EXPLANATION");
                deterministic.add(row);
            }
            sentences.add(text);
        }

        if (sentences.isEmpty() && outcome != null) {
            sentences.add(defaultOutcomeSentence(outcome, percentageVariance));
        }

        String freeText = String.join(" ", sentences);
        return new ExplanationBundle(freeText, codeNames, facts, deterministic, probable);
    }

    public String methodVersion() {
        return ReconciliationConstants.RECON_EXPLANATION_V1;
    }

    private static boolean isProbable(ExplanationCode code) {
        return switch (code) {
            case CREDIT_SALES_TIMING, RECEIVABLE_COLLECTION_LAG, ADVANCE_CUSTOMER_RECEIPTS,
                 CASH_SALES_NOT_BANKED, UNKNOWN_LENDER_MATCH -> true;
            default -> false;
        };
    }

    private static String textFor(ExplanationCode code) {
        return switch (code) {
            case PERIOD_MISMATCH -> "Periods are not comparable under the alignment strategy.";
            case PARTIAL_GST -> "GST coverage is partial for the comparison window.";
            case PARTIAL_BANK_STATEMENT -> "Bank statement coverage is incomplete for the comparison window.";
            case PRESUMPTIVE_ITR -> "ITR is presumptive; business turnover may not be directly comparable.";
            case CREDIT_SALES_TIMING -> "Credit sales timing may explain revenue vs collections differences.";
            case RECEIVABLE_COLLECTION_LAG -> "Receivable collection lag may explain turnover differences.";
            case ADVANCE_CUSTOMER_RECEIPTS -> "Advance customer receipts may inflate banking credits relative to GST.";
            case NON_OPERATING_BANK_CREDITS -> "Non-operating bank credits were considered in adjusted credits.";
            case SELF_TRANSFERS_EXCLUDED -> "Self transfers were excluded from adjusted banking credits.";
            case LOAN_DISBURSEMENTS_EXCLUDED -> "Loan disbursements were excluded from adjusted banking credits.";
            case CAPITAL_INFUSIONS_EXCLUDED -> "Capital infusions were excluded from adjusted banking credits.";
            case CASH_SALES_NOT_BANKED -> "Cash sales may not appear in bank credits.";
            case MULTI_GSTIN_AGGREGATION -> "Multiple GSTINs were aggregated for GST turnover.";
            case REVISED_ITR -> "A revised ITR was selected as the effective return.";
            case GST_RETURN_DELAY -> "GST return filing delays affect period completeness.";
            case BUREAU_REPORT_STALE -> "Bureau report freshness is low.";
            case BANK_EMI_NOT_DETECTED -> "Bank EMI obligations were not detected.";
            case BUREAU_EMI_MISSING -> "Bureau monthly obligation is missing.";
            case UNKNOWN_LENDER_MATCH -> "Lender-level matching between bureau and bank is ambiguous.";
            case SOURCE_LOW_CONFIDENCE -> "One or more source confidences are below minimum.";
            case MISSING_LEFT_OPERAND -> "Left operand value is missing.";
            case MISSING_RIGHT_OPERAND -> "Right operand value is missing.";
            case SUBJECT_MISMATCH -> "Subject entities across sources do not match.";
            case VALUES_ALIGNED -> "Left and right values are aligned within match tolerance.";
            case WITHIN_WARNING_TOLERANCE -> "Variance is within warning tolerance.";
            case MATERIAL_DIFFERENCE -> "Variance exceeds material tolerance and warrants review.";
            case CONFLICTING_SOURCES -> "Sources conflict beyond material tolerance.";
            case LEGACY_METRIC_WRAP -> "Result wrapped from a legacy C2/C4 variance metric for parity.";
        };
    }

    private static String defaultOutcomeSentence(ReconciliationOutcome outcome, BigDecimal pct) {
        String pctPart = pct != null ? " Variance " + pct.toPlainString() + "%." : "";
        return switch (outcome) {
            case MATCH -> "Values match." + pctPart;
            case ACCEPTABLE_VARIANCE -> "Variance is acceptable." + pctPart;
            case MATERIAL_VARIANCE -> "Material variance detected; human review recommended." + pctPart;
            case CONFLICT -> "Sources conflict materially." + pctPart;
            case DATA_INSUFFICIENT -> "Insufficient data to reconcile.";
            case NOT_APPLICABLE -> "Reconciliation is not applicable.";
            case ERROR -> "Reconciliation evaluation error.";
        };
    }
}
