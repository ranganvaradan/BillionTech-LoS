package com.los.core.controller;

import com.los.core.service.report.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "BR-15.5: Generate exportable report data")
    public ResponseEntity<Map<String, Object>> exportReport(
            @PathVariable String reportType,
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(reportExportService.generateExportData(reportType, format, startDate, endDate));
    }
}
