package com.los.core.service.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            case "DRIVING_LICENSE" -> extractDrivingLicense(documentId);
            case "VOTER_ID" -> extractVoterId(documentId);
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
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", "BANK_STATEMENT",
                "status", "EXTRACTED",
                "confidence", 0.85,
                "extractedData", Map.of(
                        "bankName", "HDFC Bank",
                        "accountNumber", "XXXX1234",
                        "accountHolder", "Rahul Kumar Sharma",
                        "statementPeriod", "Jan 2026 - Mar 2026",
                        "openingBalance", 125000,
                        "closingBalance", 185000,
                        "totalCredits", 450000,
                        "totalDebits", 390000,
                        "transactionCount", 47
                ),
                "analysisReady", true
        );
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
        return Map.of(
                "documentId", documentId.toString(),
                "documentType", "ITR",
                "status", "EXTRACTED",
                "confidence", 0.87,
                "extractedData", Map.of(
                        "assessmentYear", "2025-26",
                        "panNumber", "ABCDE1234F",
                        "grossTotalIncome", 1200000,
                        "totalTaxPaid", 125000,
                        "itrForm", "ITR-1",
                        "filingDate", "2025-07-15"
                )
        );
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
}
