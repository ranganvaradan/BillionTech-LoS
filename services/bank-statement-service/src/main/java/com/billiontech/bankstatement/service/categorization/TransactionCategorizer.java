package com.billiontech.bankstatement.service.categorization;

import com.billiontech.bankstatement.model.entity.BankTransaction;
import com.billiontech.bankstatement.model.enums.TransactionCategory;
import com.billiontech.bankstatement.model.enums.TransactionChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TransactionCategorizer {

    @Value("${bankstatement.analysis.salary-keywords:SALARY,SAL CR,WAGES,PAYROLL,STIPEND,REMUNERATION}")
    private String salaryKeywords;

    @Value("${bankstatement.analysis.emi-keywords:EMI,LOAN,EQUATED MONTHLY,REPAYMENT}")
    private String emiKeywords;

    @Value("${bankstatement.analysis.rent-keywords:RENT,HOUSE RENT,RENTAL}")
    private String rentKeywords;

    @Value("${bankstatement.analysis.insurance-keywords:LIC,INSURANCE,PREMIUM,POLICY}")
    private String insuranceKeywords;

    @Value("${bankstatement.analysis.utility-keywords:ELECTRICITY,WATER,GAS,BROADBAND,MOBILE,RECHARGE,TELECOM,AIRTEL,JIO,VI,BSNL}")
    private String utilityKeywords;

    @Value("${bankstatement.analysis.investment-keywords:MUTUAL FUND,MF,SIP,FD,FIXED DEPOSIT,RD,RECURRING,SHARE,STOCK,DEMAT,ZERODHA,GROWW}")
    private String investmentKeywords;

    @Value("${bankstatement.analysis.bounce-keywords:BOUNCE,CHQ RETURN,CHEQUE RETURN,INSTRUMENT RETURN,DISHONOUR,UNPAID,INSUFFICIENT,ECS RETURN,NACH RETURN}")
    private String bounceKeywords;

    @Value("${bankstatement.analysis.reversal-keywords:REVERSAL,REVERSED,REFUND,CASHBACK}")
    private String reversalKeywords;

    @Value("${bankstatement.analysis.government-keywords:TAX,GST,TDS,CHALLAN,INCOME TAX,GOVT}")
    private String governmentKeywords;

    public void categorize(BankTransaction txn) {
        String narration = txn.getNarration() != null ? txn.getNarration().toUpperCase() : "";
        txn.setRawDescription(txn.getNarration());

        // Detect channel
        txn.setChannel(detectChannel(narration));

        // Detect category
        txn.setCategory(detectCategory(narration, txn));

        // Detect bounce (only override if no more specific category was detected)
        if (matchesAny(narration, bounceKeywords)) {
            txn.setIsBounce(true);
            if (txn.getCategory() == TransactionCategory.OTHER || txn.getCategory() == TransactionCategory.CHEQUE) {
                txn.setCategory(TransactionCategory.BOUNCE_RETURN);
            }
        }

        // Detect reversal
        if (matchesAny(narration, reversalKeywords)) {
            txn.setIsReversal(true);
            if (txn.getCategory() == TransactionCategory.OTHER) {
                txn.setCategory(TransactionCategory.REVERSAL);
            }
        }

        // Extract counterparty from narration
        extractCounterparty(txn, narration);
    }

    private TransactionCategory detectCategory(String narration, BankTransaction txn) {
        boolean isCredit = txn.getCreditAmount() != null && txn.getCreditAmount().compareTo(BigDecimal.ZERO) > 0;

        if (matchesAny(narration, salaryKeywords) && isCredit) return TransactionCategory.SALARY;
        if (matchesAny(narration, rentKeywords)) return TransactionCategory.RENT;
        if (matchesAny(narration, insuranceKeywords)) return TransactionCategory.INSURANCE;
        if (matchesAny(narration, emiKeywords)) return TransactionCategory.EMI_LOAN;
        if (matchesAny(narration, utilityKeywords)) return TransactionCategory.UTILITIES;
        if (matchesAny(narration, governmentKeywords)) return TransactionCategory.GOVERNMENT;
        if (matchesAny(narration, investmentKeywords)) return TransactionCategory.INVESTMENT;
        if (matchesAny(narration, bounceKeywords)) return TransactionCategory.BOUNCE_RETURN;
        if (matchesAny(narration, reversalKeywords)) return TransactionCategory.REVERSAL;

        if (narration.contains("ATM") || narration.contains("CASH WDR") || narration.contains("CASH WITHDRAWAL")) {
            return TransactionCategory.CASH_WITHDRAWAL;
        }
        if (narration.contains("CASH DEP") || narration.contains("CASH DEPOSIT") || narration.contains("CDM")) {
            return TransactionCategory.CASH_DEPOSIT;
        }
        if (narration.contains("POS") || narration.contains("SWIPE") || narration.contains("ECOM")) {
            return TransactionCategory.POS_SHOPPING;
        }
        if (narration.contains("CHQ") || narration.contains("CHEQUE") || narration.contains("CLG")) {
            return TransactionCategory.CHEQUE;
        }
        if (narration.contains("INT.") || narration.contains("INTEREST")) {
            return isCredit ? TransactionCategory.INTEREST_CREDIT : TransactionCategory.INTEREST_DEBIT;
        }
        if (narration.contains("CHARGE") || narration.contains("FEE") || narration.contains("MAINTENANCE")) {
            return TransactionCategory.BANK_CHARGES;
        }
        if (narration.contains("TRANSFER") || narration.contains("TRF")
                || narration.contains("NEFT") || narration.contains("RTGS")
                || narration.contains("IMPS") || narration.contains("UPI")) {
            return TransactionCategory.TRANSFER;
        }

        return TransactionCategory.OTHER;
    }

    private TransactionChannel detectChannel(String narration) {
        if (narration.contains("UPI")) return TransactionChannel.UPI;
        if (narration.contains("NEFT")) return TransactionChannel.NEFT;
        if (narration.contains("RTGS")) return TransactionChannel.RTGS;
        if (narration.contains("IMPS")) return TransactionChannel.IMPS;
        if (narration.contains("NACH")) return TransactionChannel.NACH;
        if (narration.contains("ECS")) return TransactionChannel.ECS;
        if (narration.contains("ATM")) return TransactionChannel.ATM;
        if (narration.contains("POS") || narration.contains("SWIPE")) return TransactionChannel.POS;
        if (narration.contains("CHQ") || narration.contains("CHEQUE") || narration.contains("CLG")) return TransactionChannel.CHEQUE;
        if (narration.contains("CASH")) return TransactionChannel.CASH;
        if (narration.contains("MOB") || narration.contains("MOBILE")) return TransactionChannel.MOBILE_BANKING;
        if (narration.contains("NET") || narration.contains("INTERNET") || narration.contains("INB")) return TransactionChannel.INTERNET_BANKING;
        if (narration.contains("DEMAND DRAFT") || matchesWord(narration, "DD")) return TransactionChannel.DEMAND_DRAFT;
        if (narration.contains("AUTO") || narration.contains("STANDING") || matchesWord(narration, "SI")) return TransactionChannel.AUTO_DEBIT;
        return TransactionChannel.OTHER;
    }

    private void extractCounterparty(BankTransaction txn, String narration) {
        // Extract counterparty from UPI transactions: UPI/counterparty@upiid/...
        if (narration.contains("UPI")) {
            String[] parts = narration.split("[/\\-]");
            if (parts.length >= 2) {
                String counterparty = parts[1].trim();
                if (!counterparty.isEmpty() && counterparty.length() > 2) {
                    txn.setCounterpartyName(counterparty);
                }
            }
        }
        // NEFT/RTGS: usually contains beneficiary name after certain keywords
        if (narration.contains("NEFT") || narration.contains("RTGS") || narration.contains("IMPS")) {
            String[] parts = narration.split("[/\\-]");
            if (parts.length >= 3) {
                txn.setCounterpartyName(parts[parts.length - 1].trim());
            }
        }
    }

    private boolean matchesWord(String text, String keyword) {
        Pattern p = Pattern.compile("(?<![A-Z])" + Pattern.quote(keyword) + "(?![A-Z])");
        return p.matcher(text).find();
    }

    private boolean matchesAny(String text, String keywordsCsv) {
        if (keywordsCsv == null || keywordsCsv.isBlank()) return false;
        String[] keywords = keywordsCsv.split(",");
        for (String keyword : keywords) {
            String kw = keyword.trim();
            if (kw.length() <= 3) {
                // Short keywords use word-boundary matching to avoid substring false positives
                // (e.g. "PAY" should not match "PAYTM", "EMI" should not match "PREMIUM")
                Pattern p = Pattern.compile("(?<![A-Z])" + Pattern.quote(kw) + "(?![A-Z])");
                if (p.matcher(text).find()) return true;
            } else {
                if (text.contains(kw)) return true;
            }
        }
        return false;
    }
}
