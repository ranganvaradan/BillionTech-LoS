package com.los.lms.controller;

import com.los.lms.dto.LoanHandoverRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS Adapter", description = "Loan Management System integration")
public class LmsAdapterController {

    @PostMapping("/handover")
    @Operation(summary = "Handover disbursed loan to LMS for servicing")
    public ResponseEntity<Map<String, Object>> handoverLoan(@RequestBody LoanHandoverRequest request) {
        log.info("Loan handover request for application: {}", request.getApplicationNumber());

        // TODO: Integrate with actual LMS system
        return ResponseEntity.ok(Map.of(
                "status", "ACCEPTED",
                "lmsReferenceId", "LMS-" + request.getApplicationNumber(),
                "message", "Loan handed over to LMS for servicing"
        ));
    }

    @GetMapping("/status/{applicationNumber}")
    @Operation(summary = "Check LMS handover status")
    public ResponseEntity<Map<String, Object>> checkStatus(@PathVariable String applicationNumber) {
        return ResponseEntity.ok(Map.of(
                "applicationNumber", applicationNumber,
                "lmsStatus", "ACTIVE",
                "message", "Loan is active in LMS"
        ));
    }
}
