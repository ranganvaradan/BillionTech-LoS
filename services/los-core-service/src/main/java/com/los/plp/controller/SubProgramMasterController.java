package com.los.plp.controller;

import com.los.plp.model.dto.SubProgramMasterRequest;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.service.SubProgramMasterService;
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
@RequestMapping("/api/v1/sub-programs")
@RequiredArgsConstructor
@Tag(name = "PLP Sub-programs", description = "Sub-program master setup and PLP sync")
public class SubProgramMasterController {

    private final SubProgramMasterService subProgramMasterService;

    @PostMapping
    @Operation(summary = "Create sub-program master and sync to PLP")
    public ResponseEntity<SubProgramMaster> create(@Valid @RequestBody SubProgramMasterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subProgramMasterService.create(request));
    }

    @PutMapping("/{subProgramId}")
    @Operation(summary = "Update sub-program master and sync to PLP")
    public ResponseEntity<SubProgramMaster> update(
            @PathVariable UUID subProgramId,
            @Valid @RequestBody SubProgramMasterRequest request) {
        return ResponseEntity.ok(subProgramMasterService.update(subProgramId, request));
    }

    @GetMapping("/{subProgramId}")
    public ResponseEntity<SubProgramMaster> get(@PathVariable UUID subProgramId) {
        return ResponseEntity.ok(subProgramMasterService.get(subProgramId));
    }

    @GetMapping
    public ResponseEntity<List<SubProgramMaster>> list() {
        return ResponseEntity.ok(subProgramMasterService.list());
    }
}
