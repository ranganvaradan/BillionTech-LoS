package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankingConstants;
import com.los.core.creditintelligence.banking.domain.CashFlowClass;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import com.los.core.creditintelligence.banking.domain.TxnMode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic narration-pattern classifier. No AI. Unknown stays UNKNOWN.
 */
@Component
public class BankTransactionClassifier {

    private static final Pattern EMI = Pattern.compile(
            "\\b(EMI|EQUATED\\s*MONTHLY|LOAN\\s*EMI|NACH.*EMI|ECS.*EMI)\\b|HDFC\\s*BANK\\s*EMI|BAJAJ\\s*FINSERV\\s*EMI|BAJAJ\\s*FINANCE\\s*EMI",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NACH_RETURN = Pattern.compile(
            "\\b(NACH\\s*(RETURN|BOUNCE|REJECT|FAILED)|ECS\\s*(RETURN|BOUNCE|REJECT)|ACH\\s*(RETURN|BOUNCE|REJECT))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEQUE_RETURN = Pattern.compile(
            "\\b(CHEQUE\\s*(RETURN|BOUNCE|RETURNED)|CHQ\\s*(RETURN|BOUNCE)|INSUFFICIENT\\s*FUNDS.*CH(EQUE|Q))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CASH_DEPOSIT = Pattern.compile(
            "\\b(CASH\\s*DEPOSIT|CASH\\s*DEP|CDN|BY\\s*CASH)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SELF_TRANSFER = Pattern.compile(
            "\\b(SELF\\s*TRANSFER|OWN\\s*A/?C|TO\\s*SELF|SELF\\s*A/?C|INTERNAL\\s*TRANSFER\\s*SELF)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOAN_DISB = Pattern.compile(
            "\\b(LOAN\\s*DISBURS|DISBURSEMENT|TERM\\s*LOAN\\s*CREDIT)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GST = Pattern.compile(
            "\\b(GST\\s*(PAYMENT|PAID|PMT)|CGST|SGST|IGST)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTEREST = Pattern.compile(
            "\\b(INTEREST\\s*(CREDIT|CR|PAID)|INT\\s*CR|SB\\s*INT)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_CHARGE = Pattern.compile(
            "\\b(BANK\\s*CHARGE|SERVICE\\s*CHARGE|SMS\\s*CHARGE|AMC\\s*CHARGE|PENALTY\\s*CHARGE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAPITAL = Pattern.compile(
            "\\b(CAPITAL\\s*INFUSION|PROMOTER\\s*INFUSION|OWNER\\s*CONTRIBUTION)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPLIER = Pattern.compile(
            "\\b(SUPPLIER|VENDOR\\s*PMT|VENDOR\\s*PAYMENT)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CUSTOMER = Pattern.compile(
            "\\b(CUSTOMER\\s*(RECEIPT|PAYMENT|PMT)|NEFT.*CR|UPI.*CR|RTGS.*CR)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REFUND = Pattern.compile(
            "\\b(REFUND|REVERSAL\\s*CREDIT)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QR_SETTLEMENT = Pattern.compile(
            "\\b(QR\\s*(SETTLEMENT|SETTLE|CREDIT|CR)|UPI\\s*QR|BHARAT\\s*QR|BHQR)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLINE_GAMING = Pattern.compile(
            "\\b(DREAM11|MY11CIRCLE|RUMMYCIRCLE|ONLINE\\s*GAMING|BETTING|FANTASY\\s*SPORT)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERCOMPANY = Pattern.compile(
            "\\b(INTER\\s*-?\\s*COMPANY|INTERCOMPANY|GROUP\\s*COMPANY\\s*TRANSFER)\\b",
            Pattern.CASE_INSENSITIVE);
    /** False-positive guard: "goods return" / "sales return" must not become CHEQUE_RETURN. */
    private static final Pattern GOODS_RETURN = Pattern.compile(
            "\\b(GOODS\\s*RETURN|SALES\\s*RETURN|PURCHASE\\s*RETURN|RETURN\\s*INWARD)\\b",
            Pattern.CASE_INSENSITIVE);

    public record ClassificationResult(
            TxnCategory category,
            CashFlowClass cashFlowClass,
            String method,
            String classifierVersion,
            BigDecimal confidence,
            Map<String, Object> evidence,
            boolean emiFlag,
            boolean bounceFlag,
            boolean returnFlag,
            boolean cashFlag,
            boolean selfTransferFlag,
            boolean lenderFlag,
            boolean taxPaymentFlag,
            boolean businessReceiptFlag,
            boolean businessPaymentFlag) {
    }

    public ClassificationResult classify(String narration, TxnDirection direction, TxnMode mode) {
        String raw = narration != null ? narration.trim() : "";
        String normalized = normalize(raw);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("narrationNormalized", normalized);
        evidence.put("direction", direction != null ? direction.name() : null);
        evidence.put("mode", mode != null ? mode.name() : null);

        if (normalized.isEmpty()) {
            return unknown(evidence, "EMPTY_NARRATION");
        }

        // Bounce / return first (but not goods-return false positives)
        if (!GOODS_RETURN.matcher(normalized).find()) {
            if (NACH_RETURN.matcher(normalized).find()) {
                evidence.put("matchedPattern", "NACH_RETURN");
                return result(TxnCategory.NACH_RETURN, CashFlowClass.FINANCING, 0.95, evidence,
                        false, true, true, false, false, true, false, false, false);
            }
            if (CHEQUE_RETURN.matcher(normalized).find()) {
                evidence.put("matchedPattern", "CHEQUE_RETURN");
                return result(TxnCategory.CHEQUE_RETURN, CashFlowClass.OPERATING, 0.95, evidence,
                        false, true, true, false, false, false, false, false, false);
            }
        } else {
            evidence.put("goodsReturnExcluded", true);
        }

        if (EMI.matcher(normalized).find() && direction == TxnDirection.DEBIT) {
            evidence.put("matchedPattern", "EMI");
            return result(TxnCategory.EMI, CashFlowClass.FINANCING, 0.92, evidence,
                    true, false, false, false, false, true, false, false, false);
        }
        if (LOAN_DISB.matcher(normalized).find() && direction == TxnDirection.CREDIT) {
            evidence.put("matchedPattern", "LOAN_DISBURSEMENT");
            return result(TxnCategory.LOAN_DISBURSEMENT, CashFlowClass.FINANCING, 0.9, evidence,
                    false, false, false, false, false, true, false, false, false);
        }
        if (SELF_TRANSFER.matcher(normalized).find()) {
            evidence.put("matchedPattern", "SELF_TRANSFER");
            return result(TxnCategory.SELF_TRANSFER, CashFlowClass.TRANSFER, 0.9, evidence,
                    false, false, false, false, true, false, false, false, false);
        }
        if (CASH_DEPOSIT.matcher(normalized).find() && direction == TxnDirection.CREDIT) {
            evidence.put("matchedPattern", "CASH_DEPOSIT");
            return result(TxnCategory.CASH_DEPOSIT, CashFlowClass.OPERATING, 0.9, evidence,
                    false, false, false, true, false, false, false, false, false);
        }
        if (CAPITAL.matcher(normalized).find() && direction == TxnDirection.CREDIT) {
            evidence.put("matchedPattern", "CAPITAL_INFUSION");
            return result(TxnCategory.CAPITAL_INFUSION, CashFlowClass.FINANCING, 0.88, evidence,
                    false, false, false, false, false, false, false, false, false);
        }
        if (GST.matcher(normalized).find() && direction == TxnDirection.DEBIT) {
            evidence.put("matchedPattern", "GST_PAYMENT");
            return result(TxnCategory.GST_PAYMENT, CashFlowClass.TAX, 0.9, evidence,
                    false, false, false, false, false, false, true, false, false);
        }
        if (INTEREST.matcher(normalized).find()) {
            TxnCategory cat = direction == TxnDirection.CREDIT
                    ? TxnCategory.INTEREST_CREDIT : TxnCategory.INTEREST_DEBIT;
            evidence.put("matchedPattern", "INTEREST");
            return result(cat, CashFlowClass.FINANCING, 0.85, evidence,
                    false, false, false, false, false, false, false, false, false);
        }
        if (BANK_CHARGE.matcher(normalized).find()) {
            evidence.put("matchedPattern", "BANK_CHARGE");
            return result(TxnCategory.BANK_CHARGE, CashFlowClass.OPERATING, 0.88, evidence,
                    false, false, false, false, false, false, false, false, false);
        }
        if (REFUND.matcher(normalized).find() && direction == TxnDirection.CREDIT) {
            evidence.put("matchedPattern", "REFUND");
            return result(TxnCategory.REFUND, CashFlowClass.OPERATING, 0.8, evidence,
                    false, false, false, false, false, false, false, false, false);
        }
        if (SUPPLIER.matcher(normalized).find() && direction == TxnDirection.DEBIT) {
            evidence.put("matchedPattern", "SUPPLIER_PAYMENT");
            return result(TxnCategory.SUPPLIER_PAYMENT, CashFlowClass.OPERATING, 0.75, evidence,
                    false, false, false, false, false, false, false, false, true);
        }
        if (CUSTOMER.matcher(normalized).find() && direction == TxnDirection.CREDIT) {
            evidence.put("matchedPattern", "CUSTOMER_RECEIPT");
            return result(TxnCategory.CUSTOMER_RECEIPT, CashFlowClass.OPERATING, 0.75, evidence,
                    false, false, false, false, false, false, false, true, false);
        }
        if (QR_SETTLEMENT.matcher(normalized).find() && direction == TxnDirection.CREDIT) {
            evidence.put("matchedPattern", "QR_SETTLEMENT");
            return result(TxnCategory.QR_SETTLEMENT, CashFlowClass.OPERATING, 0.82, evidence,
                    false, false, false, false, false, false, false, true, false);
        }
        if (ONLINE_GAMING.matcher(normalized).find()) {
            evidence.put("matchedPattern", "ONLINE_GAMING");
            return result(TxnCategory.ONLINE_GAMING, CashFlowClass.OPERATING, 0.8, evidence,
                    false, false, false, false, false, false, false, false, false);
        }
        if (INTERCOMPANY.matcher(normalized).find()) {
            evidence.put("matchedPattern", "INTERCOMPANY");
            return result(TxnCategory.INTERCOMPANY, CashFlowClass.TRANSFER, 0.85, evidence,
                    false, false, false, false, true, false, false, false, false);
        }

        // Mode-assisted soft hints for operating credits/debits without specific narration
        if (direction == TxnDirection.CREDIT
                && (mode == TxnMode.NEFT || mode == TxnMode.UPI || mode == TxnMode.RTGS || mode == TxnMode.IMPS)) {
            evidence.put("matchedPattern", "MODE_OPERATING_CREDIT");
            return result(TxnCategory.OTHER_OPERATING, CashFlowClass.OPERATING, 0.55, evidence,
                    false, false, false, false, false, false, false, true, false);
        }

        return unknown(evidence, "NO_PATTERN");
    }

    public static String normalize(String narration) {
        if (narration == null) {
            return "";
        }
        return narration.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private ClassificationResult unknown(Map<String, Object> evidence, String reason) {
        evidence.put("reason", reason);
        return result(TxnCategory.UNKNOWN, CashFlowClass.UNKNOWN, 0.0, evidence,
                false, false, false, false, false, false, false, false, false);
    }

    private ClassificationResult result(
            TxnCategory category,
            CashFlowClass cashFlowClass,
            double confidence,
            Map<String, Object> evidence,
            boolean emi, boolean bounce, boolean ret, boolean cash, boolean self,
            boolean lender, boolean tax, boolean receipt, boolean payment) {
        return new ClassificationResult(
                category,
                cashFlowClass,
                BankingConstants.BANK_TXN_CLASSIFIER_V1,
                BankingConstants.BANK_TXN_CLASSIFIER_V1,
                BigDecimal.valueOf(confidence),
                evidence,
                emi, bounce, ret, cash, self, lender, tax, receipt, payment);
    }
}
