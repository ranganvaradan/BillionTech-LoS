package com.los.core.creditintelligence.policystudio.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NBFC-oriented business data element catalogue for Policy Implementability.
 * Availability is never invented here — callers overlay {@link PolicyAuthoringRegistry} status.
 * Primary/fallback describe the intended single-NBFC sourcing model (not multi-provider reconciliation).
 */
public final class BusinessDataSourceCatalog {

    private BusinessDataSourceCatalog() {}

    public static Map<String, Object> describe(String path) {
        String code = path == null ? "" : path.trim();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataElementCode", code);
        m.put("businessName", businessName(code));
        m.put("description", description(code));
        m.put("category", category(code));
        Profile p = profile(code);
        m.put("primarySource", p.primary);
        m.put("fallbackSources", p.fallbacks);
        m.put("manualCaptureAllowed", p.manualAllowed);
        m.put("missingDataBehaviour", p.missingBehaviour);
        return m;
    }

    public static String businessName(String code) {
        if (code == null || code.isBlank()) {
            return "Unmapped data element";
        }
        return switch (code) {
            case "bureau.score" -> "Bureau Score";
            case "bureau.status_ntc" -> "NTC / Thin-file Status";
            case "bureau.max_dpd_6m" -> "Maximum DPD (6 months)";
            case "bureau.inquiries.current_month" -> "Current Month Inquiries";
            case "bureau.cc_overdue_amount" -> "Credit Card Overdue Amount";
            case "bureau.overdue.amount" -> "Loan Overdue Amount";
            case "bureau.overdue.age_months" -> "Overdue Reporting Age";
            case "bureau.credit_after_overdue.exists" -> "Credit After Overdue";
            case "bureau.credit_after_overdue.clean_history_months" -> "Clean History Months (post-overdue)";
            case "bureau.emi.monthly" -> "Bureau Monthly EMI Obligation";
            case "banking.avg_daily_balance_3m" -> "Average Daily Balance (3 months)";
            case "banking.transaction_count.average_monthly_3m" -> "Average Monthly Transaction Count (3 months)";
            case "banking.settlement.avg_daily_3m" -> "QR / Settlement Average Daily (3 months)";
            case "banking.settlement.count_monthly_avg_3m" -> "Average Monthly Settlement Count (3 months)";
            case "banking.inward_return.ratio_3m" -> "Inward Return Ratio (3 months)";
            case "banking.inward_return.count_3m" -> "Inward Return Count (3 months)";
            case "gst.turnover.trailing_12m" -> "GST Turnover (12 months)";
            case "bank.turnover.trailing_12m" -> "Banking Turnover (12 months)";
            case "itr.turnover.trailing_12m" -> "ITR Turnover";
            case "application.proposed_edi" -> "Proposed EDI";
            case "application.loan_amount" -> "Proposed Loan Amount";
            case "kyc.pan.verified" -> "PAN Verification";
            case "kyc.pan.present" -> "PAN Present";
            case "kyc.pan.name_match" -> "PAN Name Match";
            case "kyc.ckyc.verified" -> "CKYC Verification";
            case "kyc.ckyc.available" -> "CKYC Available";
            case "kyc.aadhaar.verified" -> "Aadhaar Verification";
            case "kyc.gstin.verified" -> "GSTIN Verification";
            case "kyc.gstin.present" -> "GSTIN Present";
            case "kyc.cin.verified" -> "CIN / MCA Verification";
            case "kyc.cin.present" -> "CIN Present";
            case "kyc.udyam.verified" -> "Udyam Verification";
            case "kyc.bank_account.verified" -> "Bank Account Penny-Drop";
            case "kyc.vkyc.completed" -> "VKYC Completion";
            case "kyc.pkyc.completed" -> "Physical KYC Completion";
            case "kyc.overall.outcome" -> "KYC Overall Outcome";
            default -> humanize(code);
        };
    }

    private static String description(String code) {
        if (code == null) {
            return "";
        }
        if (code.startsWith("kyc.")) {
            return "KYC / identity verification fact for Decision Policy (business outcome, not provider call)";
        }
        if (code.startsWith("bureau.")) {
            return "Bureau / credit-report derived business measure";
        }
        if (code.startsWith("banking.") || code.startsWith("bank.")) {
            return "Banking / account-aggregator derived business measure";
        }
        if (code.startsWith("gst.")) {
            return "GST-derived business measure";
        }
        if (code.startsWith("itr.")) {
            return "ITR-derived business measure";
        }
        if (code.startsWith("application.")) {
            return "Application-form / loan origination field";
        }
        return "Policy data element";
    }

    public static String category(String code) {
        if (code == null) {
            return "Other";
        }
        if (code.startsWith("kyc.") || code.contains("kyc")) {
            return "KYC";
        }
        if (code.startsWith("bureau.")) {
            return "Bureau";
        }
        if (code.startsWith("banking.") || code.startsWith("bank.")) {
            return "Banking";
        }
        if (code.startsWith("gst.")) {
            return "GST";
        }
        if (code.startsWith("itr.")) {
            return "ITR";
        }
        if (code.startsWith("application.")) {
            return "Application";
        }
        return "Other";
    }

    private static Profile profile(String code) {
        String c = code == null ? "" : code.toLowerCase(Locale.ROOT);
        if (c.startsWith("kyc.pan")) {
            return new Profile("Configured PAN identity provider", List.of("Fallback PAN provider (outage only)"),
                    true, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.ckyc")) {
            return new Profile("Configured CKYC provider", List.of("Fallback CKYC provider (outage only)"),
                    false, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.aadhaar")) {
            return new Profile("Configured Aadhaar OTP provider", List.of(), false, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.gstin")) {
            return new Profile("Configured GSTIN verification provider", List.of(), true, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.cin")) {
            return new Profile("Configured MCA21 / CIN provider", List.of(), true, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.udyam")) {
            return new Profile("Configured Udyam provider", List.of(), false, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.bank_account")) {
            return new Profile("Configured bank penny-drop provider", List.of("Fallback bank verify (outage only)"),
                    false, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.vkyc") || c.startsWith("kyc.pkyc")) {
            return new Profile("Configured VKYC provider", List.of("Physical KYC (PKYC) where allowed"),
                    true, "MISSING_INFORMATION");
        }
        if (c.startsWith("kyc.")) {
            return new Profile("Configured KYC integration", List.of(), false, "MISSING_INFORMATION");
        }
        if (c.startsWith("bureau.")) {
            return new Profile("Equifax", List.of("CRIF"), false, "DATA_INSUFFICIENT");
        }
        if (c.startsWith("banking.") || c.startsWith("bank.")) {
            return new Profile("Account Aggregator", List.of("Bank statement upload"), false, "DATA_INSUFFICIENT");
        }
        if (c.startsWith("gst.")) {
            return new Profile("Configured GST provider", List.of(), false, "DATA_INSUFFICIENT");
        }
        if (c.startsWith("itr.")) {
            return new Profile("Configured ITR provider", List.of(), false, "DATA_INSUFFICIENT");
        }
        if (c.startsWith("application.") || c.contains("proposed_edi")) {
            return new Profile("Application form", List.of(), true, "DATA_INSUFFICIENT");
        }
        return new Profile("Configured lender source", List.of(), false, "DATA_INSUFFICIENT");
    }

    private static String humanize(String code) {
        String s = code.replace("application.", "").replace("banking.", "").replace("bureau.", "")
                .replace("gst.", "").replace("itr.", "").replace("bank.", "").replace("kyc.", "")
                .replace('_', ' ').replace('.', ' ');
        if (s.isEmpty()) {
            return code;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private record Profile(String primary, List<String> fallbacks, boolean manualAllowed, String missingBehaviour) {}

    /** Configured family sources for the Data Sources panel (NBFC single-deployment view). */
    public static List<Map<String, Object>> configuredSourceFamilies() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(sourceFamily("Bureau", "Equifax", "CRIF", "CONSUMER_BUREAU"));
        list.add(sourceFamily("Banking", "Account Aggregator", "Bank statement upload", "BANK_STATEMENT"));
        list.add(sourceFamily("GST", "Configured GST provider", null, "GST"));
        list.add(sourceFamily("ITR", "Configured ITR provider", null, "ITR"));
        list.add(sourceFamily("Application", "Application form", null, "APPLICATION"));
        list.add(sourceFamily("KYC — PAN", "Configured PAN identity provider", "Fallback PAN provider (outage only)", "IDENTITY_PAN"));
        list.add(sourceFamily("KYC — CKYC", "Configured CKYC provider", "Fallback CKYC provider (outage only)", "IDENTITY_CKYC"));
        list.add(sourceFamily("KYC — Aadhaar", "Configured Aadhaar OTP provider", null, "IDENTITY_AADHAAR"));
        list.add(sourceFamily("KYC — GSTIN", "Configured GSTIN verification provider", null, "IDENTITY_GSTIN"));
        list.add(sourceFamily("KYC — CIN/MCA", "Configured MCA21 / CIN provider", null, "IDENTITY_CIN"));
        list.add(sourceFamily("KYC — Udyam", "Configured Udyam provider", null, "IDENTITY_UDYAM"));
        list.add(sourceFamily("KYC — Bank", "Configured bank penny-drop provider", "Fallback bank verify (outage only)", "IDENTITY_BANK"));
        list.add(sourceFamily("KYC — VKYC", "Configured VKYC provider", "Physical KYC (PKYC) where allowed", "IDENTITY_VKYC"));
        return list;
    }

    private static Map<String, Object> sourceFamily(String category, String primary, String fallback, String sourceType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("primarySource", primary);
        m.put("fallbackSource", fallback);
        m.put("sourceType", sourceType);
        m.put("role", "PRIMARY");
        return m;
    }
}
