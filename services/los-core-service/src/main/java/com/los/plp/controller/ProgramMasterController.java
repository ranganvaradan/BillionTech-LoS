package com.los.plp.controller;

import com.los.plp.model.dto.ProgramMasterRequest;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.service.ProgramMasterService;
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
@RequestMapping("/api/v1/programs")
@RequiredArgsConstructor
@Tag(name = "PLP Programs", description = "Program master setup and PLP sync")
public class ProgramMasterController {

    private final ProgramMasterService programMasterService;

    @PostMapping
    @Operation(summary = "Create program master and sync to PLP")
    public ResponseEntity<ProgramMaster> create(@Valid @RequestBody ProgramMasterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programMasterService.create(request));
    }

    @PutMapping("/{programId}")
    @Operation(summary = "Update program master and sync to PLP")
    public ResponseEntity<ProgramMaster> update(
            @PathVariable UUID programId,
            @Valid @RequestBody ProgramMasterRequest request) {
        return ResponseEntity.ok(programMasterService.update(programId, request));
    }

    @GetMapping("/{programId}")
    public ResponseEntity<ProgramMaster> get(@PathVariable UUID programId) {
        return ResponseEntity.ok(programMasterService.get(programId));
    }

    @GetMapping
    public ResponseEntity<List<ProgramMaster>> list() {
        return ResponseEntity.ok(programMasterService.list());
    }
}
