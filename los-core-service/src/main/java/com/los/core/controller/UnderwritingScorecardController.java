package com.los.core.controller;

import com.los.core.model.dto.request.UnderwritingScorecardRequest;
import com.los.core.model.dto.response.UnderwritingScorecardResponse;
import com.los.core.service.underwriting.ScorecardConvergenceService;
import com.los.core.service.underwriting.UnderwritingScorecardAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/underwriting/scorecards")
@RequiredArgsConstructor
@Tag(name = "Underwriting scorecards", description = "Structured scorecard policies (parameters, thresholds, hard rules)")
public class UnderwritingScorecardController {

    private final UnderwritingScorecardAdminService adminService;
    private final ScorecardConvergenceService convergenceService;

    @GetMapping
    @Operation(summary = "List scorecards")
    public ResponseEntity<List<UnderwritingScorecardResponse>> list() {
        return ResponseEntity.ok(adminService.list());
    }

    @PostMapping
    @Operation(summary = "Create a scorecard")
    public ResponseEntity<UnderwritingScorecardResponse> create(
            @Valid @RequestBody UnderwritingScorecardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a scorecard")
    public ResponseEntity<UnderwritingScorecardResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UnderwritingScorecardRequest request) {
        return ResponseEntity.ok(adminService.update(id, request));
    }

    @PostMapping("/{id}/new-version")
    @Operation(summary = "Create DRAFT vN+1 from an existing scorecard (ACTIVE remains immutable)")
    public ResponseEntity<UnderwritingScorecardResponse> createNewVersion(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createNewVersion(id));
    }

    @PostMapping("/{id}/confirm-missing-data-policies")
    @Operation(summary = "Confirm explicit missing-data classifications on a DRAFT before activation")
    public ResponseEntity<UnderwritingScorecardResponse> confirmMissingDataPolicies(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.confirmMissingDataPolicies(id));
    }

    @GetMapping("/gacat-factors")
    @Operation(summary = "GACAT-backed factor picker catalogue for scorecard authoring")
    public ResponseEntity<Map<String, Object>> gacatFactors(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(convergenceService.factorCatalogue(q));
    }

    @GetMapping("/mapping-inventory")
    @Operation(summary = "Inventory of ACTIVE scorecard factor → GACAT mapping status")
    public ResponseEntity<Map<String, Object>> mappingInventory() {
        return ResponseEntity.ok(convergenceService.mappingInventory());
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview scorecard math without mutating an application")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(convergenceService.preview(body));
    }

    @PostMapping("/suggest-from-policy")
    @Operation(summary = "Suggest scorecard factors from policy parameters (no auto-create)")
    public ResponseEntity<Map<String, Object>> suggestFromPolicy(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = body.get("parameterIds") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return ResponseEntity.ok(convergenceService.suggestFromPolicy(ids));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a scorecard (only when inactive)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
