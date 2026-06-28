package com.los.core.controller;

import com.los.core.model.dto.request.UnderwritingRuleSetRequest;
import com.los.core.model.dto.response.UnderwritingRuleSetResponse;
import com.los.core.service.underwriting.UnderwritingRuleSetAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/underwriting/rules")
@RequiredArgsConstructor
@Tag(name = "Underwriting rules", description = "Configurable underwriting policy (Credit Manager setup)")
public class UnderwritingRuleSetController {

    private final UnderwritingRuleSetAdminService adminService;

    @GetMapping
    @Operation(summary = "List all underwriting rule sets")
    public ResponseEntity<List<UnderwritingRuleSetResponse>> list() {
        return ResponseEntity.ok(adminService.list());
    }

    @PostMapping
    @Operation(summary = "Create a rule set (inactive until activated)")
    public ResponseEntity<UnderwritingRuleSetResponse> create(@Valid @RequestBody UnderwritingRuleSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a rule set")
    public ResponseEntity<UnderwritingRuleSetResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UnderwritingRuleSetRequest request) {
        return ResponseEntity.ok(adminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an inactive rule set")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a rule set (included in policy matching)")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        adminService.activate(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a rule set")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        adminService.deactivate(id);
        return ResponseEntity.ok().build();
    }
}
