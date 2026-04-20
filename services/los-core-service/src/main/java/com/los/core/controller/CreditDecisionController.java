package com.los.core.controller;

import com.los.core.service.credit.ICreditDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credit")
@RequiredArgsConstructor
@Tag(name = "Credit Decision", description = "Automated credit evaluation engine")
public class CreditDecisionController {

    private final ICreditDecisionService creditDecisionService;

    @PostMapping("/{applicationId}/evaluate")
    @Operation(summary = "Run credit decision evaluation for an application")
    public ResponseEntity<ICreditDecisionService.CreditDecisionResult> evaluate(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(creditDecisionService.evaluate(applicationId));
    }
}
