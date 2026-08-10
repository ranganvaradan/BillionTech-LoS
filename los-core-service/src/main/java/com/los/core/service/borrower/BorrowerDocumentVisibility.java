package com.los.core.service.borrower;

import java.util.Locale;
import java.util.Set;

/**
 * Which uploaded {@code documents.document_type} values may appear on the borrower portal.
 * Lender-internal uploads (bureau evidence, CAM, manual credit worksheets, PKYC officer evidence) are hidden.
 */
public final class BorrowerDocumentVisibility {

    private static final Set<String> LENDER_ONLY = Set.of(
            "BUREAU_STATEMENT_EVIDENCE",
            "MANUAL_CREDIT_EVIDENCE",
            "PHYSICAL_KYC_EVIDENCE",
            "CAM_REPORT",
            "CAM_PDF",
            "CREDIT_APPRAISAL",
            "UNDERWRITING_MEMO",
            "UNDERWRITING_NOTE");

    private static final Set<String> KYC_TYPES = Set.of(
            "PAN_CARD",
            "AADHAAR",
            "BANK_STATEMENT",
            "PHOTOGRAPH",
            "INCOME_PROOF",
            "BORROWER_KYC",
            "PAYSLIP",
            "SALARY_SLIP",
            "GST_RETURNS",
            "GST_RETURN",
            "BUSINESS_PROOF",
            "ITR",
            "CONSTITUTION_DOCS",
            "CC_STATEMENT",
            "PAYABLES_RECEIVABLES_AGEING",
            "PROPERTY_OWNERSHIP_PROOF",
            "EXISTING_FACILITY_SANCTION",
            "EXISTING_FACILITY_STATEMENT",
            "PDC",
            "NACH_MANDATE",
            "BUREAU_REPORT",
            "COMMERCIAL_BUREAU_REPORT",
            "BOARD_RESOLUTION",
            "PURCHASE_ORDER",
            "DELIVERY_GRN",
            "TRADE_PAYMENT_RECORD",
            "BUYER_NOC",
            "PROPERTY_DOCUMENT",
            "PROPERTY_VALUATION",
            "SHARE_HOLDING_STATEMENT",
            "GOLD_PHOTO",
            "GOLD_VALUATION",
            "COLLATERAL_OTHER",
            "OTHER");

    private static final Set<String> SIGNED_UPLOAD_TYPES = Set.of(
            "SIGNED_AGREEMENT");

    private BorrowerDocumentVisibility() {
    }

    public static boolean isVisibleUploadType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return false;
        }
        String t = documentType.trim().toUpperCase(Locale.ROOT);
        if (LENDER_ONLY.contains(t)) {
            return false;
        }
        // Explicit borrower-upload KYC / SCF types win over prefix blocks (e.g. BUREAU_REPORT).
        if (KYC_TYPES.contains(t) || t.startsWith("OTHER_")) {
            return true;
        }
        if (t.startsWith("CAM_") || t.startsWith("BUREAU_") || t.startsWith("CREDIT_") || t.startsWith("UNDERWRITING_")) {
            return false;
        }
        if (SIGNED_UPLOAD_TYPES.contains(t) || t.startsWith("SIGNED_")) {
            return true;
        }
        return false;
    }

    public static String categoryForUploadType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return "KYC";
        }
        String t = documentType.trim().toUpperCase(Locale.ROOT);
        if (SIGNED_UPLOAD_TYPES.contains(t) || t.startsWith("SIGNED_")) {
            return "SIGNED";
        }
        return "KYC";
    }

    public static boolean isSignedEsignType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return false;
        }
        String t = documentType.trim().toUpperCase(Locale.ROOT);
        return t.contains("KFS")
                || t.contains("AGREEMENT")
                || t.startsWith("ESIGN_")
                || t.startsWith("SIGNED_");
    }
}
