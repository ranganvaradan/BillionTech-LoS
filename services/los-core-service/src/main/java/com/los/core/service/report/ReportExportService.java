package com.los.core.service.report;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BR-15.5: Report export (PDF/Excel/CSV).
 * BR-15.7: Channel/DSA performance reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final LoanApplicationRepository applicationRepository;

    /**
     * BR-15.5: Generate export-ready report data.
     * In production, this would use Apache POI (Excel), iText (PDF), or OpenCSV.
     */
    public Map<String, Object> generateExportData(String reportType, String format,
                                                    String startDate, String endDate) {
        log.info("Generating {} report in {} format for period {} to {}", reportType, format, startDate, endDate);

        List<LoanApplication> applications = applicationRepository.findAll();

        return switch (reportType.toUpperCase()) {
            case "MIS" -> generateMisExport(applications, format);
            case "DISBURSEMENT" -> generateDisbursementExport(applications, format);
            case "REGULATORY" -> generateRegulatoryExport(applications, format);
            case "PORTFOLIO" -> generatePortfolioExport(applications, format);
            case "DSA_PERFORMANCE" -> generateDsaPerformanceExport(format);
            default -> Map.of("error", "Unknown report type: " + reportType);
        };
    }

    private Map<String, Object> generateMisExport(List<LoanApplication> applications, String format) {
        List<Map<String, Object>> rows = applications.stream().map(app -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("applicationNumber", app.getApplicationNumber());
            row.put("borrowerType", app.getBorrowerType().name());
            row.put("loanProduct", app.getLoanProduct());
            row.put("requestedAmount", app.getRequestedAmount());
            row.put("status", app.getStatus().name());
            row.put("createdAt", app.getCreatedAt() != null ? app.getCreatedAt().toString() : "");
            row.put("submittedAt", app.getSubmittedAt() != null ? app.getSubmittedAt().toString() : "");
            return row;
        }).collect(Collectors.toList());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalApplications", applications.size());
        summary.put("totalAmount", applications.stream()
                .map(a -> a.getRequestedAmount() != null ? a.getRequestedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.put("statusBreakdown", applications.stream()
                .collect(Collectors.groupingBy(a -> a.getStatus().name(), Collectors.counting())));

        return Map.of(
                "reportType", "MIS",
                "format", format,
                "generatedAt", Instant.now().toString(),
                "summary", summary,
                "columns", List.of("applicationNumber", "borrowerType", "loanProduct",
                        "requestedAmount", "status", "createdAt", "submittedAt"),
                "rows", rows,
                "totalRows", rows.size(),
                "exportReady", true,
                "downloadUrl", "/api/v1/reports/download/mis-" + format.toLowerCase() + "-" +
                        LocalDate.now() + "." + format.toLowerCase()
        );
    }

    private Map<String, Object> generateDisbursementExport(List<LoanApplication> applications, String format) {
        List<LoanApplication> disbursed = applications.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.DISBURSED)
                .collect(Collectors.toList());

        List<Map<String, Object>> rows = disbursed.stream().map(app -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("applicationNumber", app.getApplicationNumber());
            row.put("borrowerType", app.getBorrowerType().name());
            row.put("loanProduct", app.getLoanProduct());
            row.put("sanctionedAmount", app.getRequestedAmount());
            row.put("interestRate", app.getInterestRate());
            row.put("tenureMonths", app.getTenureMonths());
            return row;
        }).collect(Collectors.toList());

        BigDecimal totalDisbursed = disbursed.stream()
                .map(a -> a.getRequestedAmount() != null ? a.getRequestedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "reportType", "DISBURSEMENT",
                "format", format,
                "generatedAt", Instant.now().toString(),
                "summary", Map.of("totalDisbursed", totalDisbursed, "loanCount", disbursed.size()),
                "columns", List.of("applicationNumber", "borrowerType", "loanProduct",
                        "sanctionedAmount", "interestRate", "tenureMonths"),
                "rows", rows,
                "totalRows", rows.size(),
                "exportReady", true
        );
    }

    private Map<String, Object> generateRegulatoryExport(List<LoanApplication> applications, String format) {
        long total = applications.size();
        long approved = applications.stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED
                || a.getStatus() == ApplicationStatus.DISBURSED).count();

        return Map.of(
                "reportType", "REGULATORY",
                "format", format,
                "generatedAt", Instant.now().toString(),
                "rbiCompliance", Map.of(
                        "totalApplications", total,
                        "approvedApplications", approved,
                        "approvalRate", total > 0 ? (approved * 100.0 / total) : 0,
                        "kfsIssuedCount", approved,
                        "kfsCoolingOffCompliant", true,
                        "aaConsentCount", approved
                ),
                "cersaiSubmissions", Map.of(
                        "totalRegistered", 0,
                        "pendingRegistration", approved,
                        "status", "PENDING"
                ),
                "exportReady", true
        );
    }

    private Map<String, Object> generatePortfolioExport(List<LoanApplication> applications, String format) {
        Map<String, Long> productBreakdown = applications.stream()
                .collect(Collectors.groupingBy(LoanApplication::getLoanProduct, Collectors.counting()));

        Map<String, BigDecimal> productAmounts = applications.stream()
                .collect(Collectors.groupingBy(LoanApplication::getLoanProduct,
                        Collectors.reducing(BigDecimal.ZERO,
                                a -> a.getRequestedAmount() != null ? a.getRequestedAmount() : BigDecimal.ZERO,
                                BigDecimal::add)));

        return Map.of(
                "reportType", "PORTFOLIO",
                "format", format,
                "generatedAt", Instant.now().toString(),
                "summary", Map.of(
                        "totalLoans", applications.size(),
                        "totalPortfolioValue", applications.stream()
                                .map(a -> a.getRequestedAmount() != null ? a.getRequestedAmount() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        "productBreakdown", productBreakdown,
                        "productAmounts", productAmounts
                ),
                "exportReady", true
        );
    }

    // BR-15.7: DSA/Channel performance reports

    private Map<String, Object> generateDsaPerformanceExport(String format) {
        // Simulated DSA performance data
        List<Map<String, Object>> dsaRows = List.of(
                Map.of("dsaCode", "DSA001", "dsaName", "FinServ Partners",
                        "applications", 120, "approved", 85, "disbursed", 72,
                        "conversionRate", "60.0%", "totalAmount", 36000000,
                        "commission", 900000),
                Map.of("dsaCode", "DSA002", "dsaName", "LoanConnect India",
                        "applications", 95, "approved", 62, "disbursed", 55,
                        "conversionRate", "57.9%", "totalAmount", 27500000,
                        "commission", 687500),
                Map.of("dsaCode", "DSA003", "dsaName", "CreditBridge",
                        "applications", 78, "approved", 58, "disbursed", 50,
                        "conversionRate", "64.1%", "totalAmount", 25000000,
                        "commission", 625000)
        );

        return Map.of(
                "reportType", "DSA_PERFORMANCE",
                "format", format,
                "generatedAt", Instant.now().toString(),
                "period", "April 2026",
                "summary", Map.of(
                        "totalDsas", 3,
                        "totalApplications", 293,
                        "totalDisbursed", 88500000,
                        "totalCommission", 2212500,
                        "averageConversionRate", "60.7%"
                ),
                "rows", dsaRows,
                "exportReady", true
        );
    }
}
