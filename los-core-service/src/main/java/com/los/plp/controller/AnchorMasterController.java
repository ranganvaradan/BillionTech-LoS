package com.los.plp.controller;

import com.los.plp.model.dto.AnchorMasterRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.service.AnchorMasterService;
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
@RequestMapping("/api/v1/anchors")
@RequiredArgsConstructor
@Tag(name = "PLP Anchors", description = "Anchor master setup and PLP sync")
public class AnchorMasterController {

    private final AnchorMasterService anchorMasterService;

    @PostMapping
    @Operation(summary = "Create anchor master and sync to PLP")
    public ResponseEntity<AnchorMaster> create(@Valid @RequestBody AnchorMasterRequest request) {
        // Reload after create: after-commit PLP sync runs before the transactional create returns.
        AnchorMaster created = anchorMasterService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(anchorMasterService.get(created.getId()));
    }

    @PutMapping("/{anchorId}")
    @Operation(summary = "Update anchor master and sync to PLP")
    public ResponseEntity<AnchorMaster> update(
            @PathVariable UUID anchorId,
            @Valid @RequestBody AnchorMasterRequest request) {
        AnchorMaster updated = anchorMasterService.update(anchorId, request);
        return ResponseEntity.ok(anchorMasterService.get(updated.getId()));
    }

    @GetMapping("/{anchorId}")
    public ResponseEntity<AnchorMaster> get(@PathVariable UUID anchorId) {
        return ResponseEntity.ok(anchorMasterService.get(anchorId));
    }

    @GetMapping
    public ResponseEntity<List<AnchorMaster>> list() {
        return ResponseEntity.ok(anchorMasterService.list());
    }
}
