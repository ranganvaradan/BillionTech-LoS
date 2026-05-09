package com.los.core.controller;

import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.demo.DemoApplicationPurgeService;
import com.los.core.service.demo.DemoModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo endpoints: always registered so clients receive explicit 404/JSON when demo mode is off.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
@Tag(name = "Demo", description = "Local/demo data utilities (not for production)")
public class DemoController {

    private final DemoModeService demoModeService;
    private final DemoApplicationPurgeService demoApplicationPurgeService;
    private final LoanApplicationRepository loanApplicationRepository;

    @GetMapping("/status")
    @Operation(summary = "Report whether demo mode is on and how many applications exist")
    public ResponseEntity<Map<String, Object>> status() {
        boolean enabled = demoModeService.isDemoModeEnabled();
        long applicationCount = loanApplicationRepository.count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("demoEnabled", enabled);
        body.put("profile", demoModeService.getActiveProfilesDisplay());
        body.put("applicationCount", applicationCount);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/applications")
    @Operation(summary = "Delete all loan applications and common dependent data (local/demo only)")
    public ResponseEntity<Map<String, Object>> deleteAllApplications() {
        if (!demoModeService.isDemoModeEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", DemoModeService.DEMO_MODE_DISABLED_MESSAGE));
        }
        try {
            int n = demoApplicationPurgeService.deleteAllApplicationsAndDependents();
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("deletedApplications", n);
            ok.put("status", "success");
            return ResponseEntity.ok(ok);
        } catch (Throwable t) {
            log.error("Demo clear failed", t);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "Failed to clear demo data");
            err.put("reason", "DEMO_DELETE_FAILED");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
}
