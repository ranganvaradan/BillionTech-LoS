package com.los.core.controller;

import com.los.core.model.dto.request.AssignmentRuleSetRequest;
import com.los.core.model.dto.response.AssignmentRuleSetResponse;
import com.los.core.service.assignment.AssignmentRuleSetAdminService;
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
@RequestMapping("/api/v1/assignment/rules")
@RequiredArgsConstructor
@Tag(name = "Assignment rules", description = "Route applications to roles/users (Credit Manager setup)")
public class AssignmentRuleSetController {

    private final AssignmentRuleSetAdminService adminService;

    @GetMapping
    @Operation(summary = "List all assignment rules")
    public ResponseEntity<List<AssignmentRuleSetResponse>> list() {
        return ResponseEntity.ok(adminService.list());
    }

    @PostMapping
    @Operation(summary = "Create (inactive until activated)")
    public ResponseEntity<AssignmentRuleSetResponse> create(@Valid @RequestBody AssignmentRuleSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update")
    public ResponseEntity<AssignmentRuleSetResponse> update(
            @PathVariable UUID id, @Valid @RequestBody AssignmentRuleSetRequest request) {
        return ResponseEntity.ok(adminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete when inactive")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        adminService.activate(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        adminService.deactivate(id);
        return ResponseEntity.ok().build();
    }
}
