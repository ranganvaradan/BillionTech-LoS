package com.los.core.controller;

import com.los.core.service.report.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * BR-15.5: Report export (PDF/Excel/CSV).
 * BR-15.7: Channel/DSA performance reports.
 */
@RestController
@RequestMapping("/api/v1/reports/export")
@RequiredArgsConstructor
@Tag(name = "Report Export", description = "Export reports in PDF, Excel, CSV formats")
public class ReportExportController {

    private final ReportExportService reportExportService;

    @GetMapping("/{reportType}")
    @Operation(summary = "BR-15.5: Generate exportable report data (JSON)")
    public ResponseEntity<Map<String, Object>> exportReport(
            @PathVariable String reportType,
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(reportExportService.generateExportData(reportType, format, startDate, endDate));
    }

    @GetMapping("/download/{reportType}.xlsx")
    @Operation(summary = "Download report as Excel (.xlsx)")
    public ResponseEntity<byte[]> downloadExcel(
            @PathVariable String reportType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        byte[] excel = reportExportService.generateExcel(reportType, startDate, endDate);
        String filename = reportType.toLowerCase() + "-report-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/download/{reportType}.pdf")
    @Operation(summary = "Download report as PDF")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable String reportType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        byte[] pdf = reportExportService.generatePdf(reportType, startDate, endDate);
        String filename = reportType.toLowerCase() + "-report-" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/download/{reportType}.csv")
    @Operation(summary = "Download report as CSV")
    public ResponseEntity<byte[]> downloadCsv(
            @PathVariable String reportType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        byte[] csv = reportExportService.generateCsv(reportType, startDate, endDate);
        String filename = reportType.toLowerCase() + "-report-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
