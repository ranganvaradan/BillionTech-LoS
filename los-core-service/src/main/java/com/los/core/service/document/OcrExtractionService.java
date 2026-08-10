package com.los.core.service.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BR-16.3: OCR auto-extraction for PAN, Aadhaar, bank statements.
 * In production, this would integrate with Google Vision, AWS Textract, or similar.
 */
@Slf4j
@Service
public class OcrExtractionService {

    /**
     * Extract data from an uploaded document using OCR.
     */
    public Map<String, Object> extractFromDocument(UUID documentId, String documentType, String documentPath) {
        log.info("OCR extraction initiated for document: {} type: {}", documentId, documentType);

        return switch (documentType.toUpperCase()) {
            case "PAN_CARD" -> extractPanCard(documentId);
            case "AADHAAR_FRONT", "AADHAAR_BACK" -> extractAadhaar(documentId, documentType);
            case "BANK_STATEMENT" -> extractBankStatement(documentId);
            case "SALARY_SLIP" -> extractSalarySlip(documentId);
            case "ITR" -> extractItr(documentId);
            case "GST_CERTIFICATE" -> extractGstCertificate(documentId);
            case "GST_RETURN", "GST_RETURNS" -> extractGstStatement(documentId, documentType);
            case "DRIVING_LICENSE" -> extractDrivingLicense(documentId);
            case "VOTER_ID" -> extractVoterId(documentId);
            case "CONSTITUTION_DOCS", "CC_STATEMENT", "PAYABLES_RECEIVABLES_AGEING",
                 "PROPERTY_OWNERSHIP_PROOF", "EXISTING_FACILITY_SANCTION", "EXISTING_FACILITY_STATEMENT",
                 "PDC", "NACH_MANDATE", "BUREAU_REPORT", "COMMERCIAL_BUREAU_REPORT",
                 "BOARD_RESOLUTION", "PURCHASE_ORDER", "DELIVERY_GRN", "TRADE_PAYMENT_RECORD",
                 "BUYER_NOC" -> extractedStub(documentId, documentType);
            default -> Map.of(
                    "documentId", documentId.toString(),
                    "documentType", documentType,
                    "status", "UNSUPPORTED",
                    "message", "OCR extraction not supported for document type: " + documentType
            );
        };
    }

    private Map<String, Object> extractPanCard(UUID documentId) {
        // Simulated OCR output
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", "PAN_CARD",
                "status", "EXTRACTED",
                "confidence", 0.95,
                "extractedData", Map.of(
                        "panNumber", "ABCDE1234F",
                        "fullName", "RAHUL KUMAR SHARMA",
                        "fatherName", "RAMESH KUMAR SHARMA",
                        "dateOfBirth", "15/06/1990",
                        "panType", "INDIVIDUAL"
                ),
                "validationStatus", "VALID"
        );
    }

    private Map<String, Object> extractAadhaar(UUID documentId, String side) {
        if ("AADHAAR_FRONT".equalsIgnoreCase(side)) {
            return Map.of(
                    "documentId", documentId.toString(),
                    "documentType", "AADHAAR_FRONT",
                    "status", "EXTRACTED",
                    "confidence", 0.92,
                    "extractedData", Map.of(
                            "aadhaarNumber", "XXXX-XXXX-1234",
                            "fullName", "Rahul Kumar Sharma",
                            "dateOfBirth", "15/06/1990",
                            "gender", "Male"
                    ),
                    "masked", true
            );
        } else {
            return Map.of(
                    "documentId", documentId.toString(),
                    "documentType", "AADHAAR_BACK",
                    "status", "EXTRACTED",
                    "confidence", 0.88,
                    "extractedData", Map.of(
                            "address", "123, Main Road, Sector 15, Gurgaon, Haryana 122001",
                            "pincode", "122001",
                            "state", "Haryana"
                    )
            );
        }
    }

    private Map<String, Object> extractBankStatement(UUID documentId) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("bankName", "HDFC Bank");
        extracted.put("accountNumber", "XXXX1234");
        extracted.put("accountHolder", "Rahul Kumar Sharma");
        extracted.put("statementPeriod", "Jan 2026 - Mar 2026");
        extracted.put("openingBalance", 125000);
        extracted.put("closingBalance", 185000);
        extracted.put("totalCredits", 450000);
        extracted.put("totalDebits", 390000);
        extracted.put("transactionCount", 47);
        extracted.put("annualBankingTurnover", 41000000);
        extracted.put("bankingTurnoverPctGst", 80);
        extracted.put("abbObligationMultiple", 1.2);
        extracted.put("ccUtilisationPct", 70);
        extracted.put("chequeBounces12m", 2);
        extracted.put("chequeBounces3m", 0);

        Map<String, Object> out = new HashMap<>();
        out.put("documentId", documentId.toString());
        out.put("documentType", "BANK_STATEMENT");
        out.put("status", "EXTRACTED");
        out.put("confidence", 0.85);
        out.put("extractedData", extracted);
        out.put("analysisReady", true);
        return out;
    }

    private Map<String, Object> extractSalarySlip(UUID documentId) {
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", "SALARY_SLIP",
                "status", "EXTRACTED",
                "confidence", 0.90,
                "extractedData", Map.of(
                        "employerName", "TechCorp India Pvt Ltd",
                        "employeeName", "Rahul Kumar Sharma",
                        "month", "March 2026",
                        "grossSalary", 95000,
                        "netSalary", 78000,
                        "basicSalary", 47500,
                        "hra", 19000,
                        "pf", 5700,
                        "tax", 8500
                )
        );
    }

    private Map<String, Object> extractItr(UUID documentId) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("assessmentYear", "2025-26");
        extracted.put("panNumber", "ABCDE1234F");
        extracted.put("grossTotalIncome", 1200000);
        extracted.put("itrIncome", 450000);
        extracted.put("pat", 500000);
        extracted.put("interestCoverage", 1.6);
        extracted.put("debtToEquity", 1.5);
        extracted.put("ebitda", 650000);
        extracted.put("debtService", 300000);
        extracted.put("tol", 3500000);
        extracted.put("tnw", 5000000);
        extracted.put("totalTaxPaid", 125000);
        extracted.put("itrForm", "ITR-1");
        extracted.put("filingDate", "2025-07-15");

        Map<String, Object> out = new HashMap<>();
        out.put("documentId", documentId.toString());
        out.put("documentType", "ITR");
        out.put("status", "EXTRACTED");
        out.put("confidence", 0.87);
        out.put("extractedData", extracted);
        return out;
    }

    private Map<String, Object> extractGstStatement(UUID documentId, String documentType) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("gstin", "07ABCDE1234F1Z5");
        extracted.put("avgGmv3m", 4200000);
        extracted.put("active90days", 1);
        extracted.put("gstIncome", 350000);
        extracted.put("annualGstTurnover", 52000000);

        Map<String, Object> out = new HashMap<>();
        out.put("documentId", documentId.toString());
        out.put("documentType", documentType.toUpperCase());
        out.put("status", "EXTRACTED");
        out.put("confidence", 0.86);
        out.put("extractedData", extracted);
        return out;
    }

    private Map<String, Object> extractGstCertificate(UUID documentId) {
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", "GST_CERTIFICATE",
                "status", "EXTRACTED",
                "confidence", 0.91,
                "extractedData", Map.of(
                        "gstin", "07ABCDE1234F1Z5",
                        "legalName", "Sharma Enterprises",
                        "tradeName", "Sharma Trading Co.",
                        "registrationDate", "2020-04-01",
                        "businessType", "Regular",
                        "state", "Delhi"
                )
        );
    }

    private Map<String, Object> extractDrivingLicense(UUID documentId) {
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", "DRIVING_LICENSE",
                "status", "EXTRACTED",
                "confidence", 0.89,
                "extractedData", Map.of(
                        "dlNumber", "HR-0619900012345",
                        "fullName", "Rahul Kumar Sharma",
                        "dateOfBirth", "15/06/1990",
                        "validUpto", "14/06/2040",
                        "issuingAuthority", "RTO Gurgaon"
                )
        );
    }

    private Map<String, Object> extractVoterId(UUID documentId) {
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", "VOTER_ID",
                "status", "EXTRACTED",
                "confidence", 0.86,
                "extractedData", Map.of(
                        "epicNumber", "ABC1234567",
                        "fullName", "Rahul Kumar Sharma",
                        "fatherName", "Ramesh Kumar Sharma",
                        "gender", "Male",
                        "address", "123, Main Road, Sector 15, Gurgaon"
                )
        );
    }

    private Map<String, Object> extractedStub(UUID documentId, String documentType) {
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", documentType.toUpperCase(),
                "status", "EXTRACTED",
                "confidence", 0.75,
                "extractedData", Map.of("acknowledged", true),
                "message", "Stub extraction for " + documentType
        );
    }
}
