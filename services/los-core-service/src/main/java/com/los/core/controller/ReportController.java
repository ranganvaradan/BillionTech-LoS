package com.los.core.controller;

import com.los.core.model.dto.response.DashboardAnalyticsResponse;
import com.los.core.model.dto.response.MisReportResponse;
import com.los.core.model.dto.response.RegulatoryReportResponse;
import com.los.core.service.report.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Dashboard analytics, MIS reports, and regulatory compliance")
public class ReportController {

    private final ReportingService reportingService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard analytics with real-time metrics")
    public ResponseEntity<DashboardAnalyticsResponse> getDashboardAnalytics() {
        return ResponseEntity.ok(reportingService.getDashboardAnalytics());
    }

    @GetMapping("/mis")
    @Operation(summary = "Generate MIS report")
    public ResponseEntity<MisReportResponse> getMisReport(
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(reportingService.generateMisReport(period));
    }

    @GetMapping("/regulatory")
    @Operation(summary = "Generate regulatory compliance report")
    public ResponseEntity<RegulatoryReportResponse> getRegulatoryReport(
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(reportingService.generateRegulatoryReport(period));
    }

    @GetMapping("/operations")
    @Operation(summary = "Operations funnel, product, handoff, and pipeline queues")
    public ResponseEntity<Map<String, Object>> getOperationsReport(
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(reportingService.getOperationsReport(period));
    }
}
