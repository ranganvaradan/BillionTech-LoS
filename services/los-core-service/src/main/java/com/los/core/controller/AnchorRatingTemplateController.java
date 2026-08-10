package com.los.core.controller;

import com.los.core.model.dto.request.AnchorRatingTemplateRequest;
import com.los.core.model.dto.response.AnchorRatingTemplateResponse;
import com.los.core.service.underwriting.AnchorRatingTemplateAdminService;
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
@RequestMapping("/api/v1/underwriting/anchor-rating-templates")
@RequiredArgsConstructor
@Tag(name = "Anchor rating templates", description = "Configurable anchor due-diligence questions and rating bands")
public class AnchorRatingTemplateController {

    private final AnchorRatingTemplateAdminService adminService;

    @GetMapping
    @Operation(summary = "List anchor rating templates")
    public ResponseEntity<List<AnchorRatingTemplateResponse>> list() {
        return ResponseEntity.ok(adminService.list());
    }

    @PostMapping
    @Operation(summary = "Create an anchor rating template")
    public ResponseEntity<AnchorRatingTemplateResponse> create(@Valid @RequestBody AnchorRatingTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an anchor rating template")
    public ResponseEntity<AnchorRatingTemplateResponse> update(
            @PathVariable UUID id, @Valid @RequestBody AnchorRatingTemplateRequest request) {
        return ResponseEntity.ok(adminService.update(id, request));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate an anchor rating template (deactivates others)")
    public ResponseEntity<AnchorRatingTemplateResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.activate(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an inactive anchor rating template")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
