package com.los.core.controller;

import com.los.core.service.esign.EmsignerDebugService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/debug")
@RequiredArgsConstructor
@Tag(name = "Internal Debug", description = "Internal read-only diagnostics")
public class InternalDebugController {

    private final EmsignerDebugService emsignerDebugService;

    @GetMapping("/emsigner-config")
    @Operation(summary = "Read-only EMSIGNER runtime config diagnostics (masked)")
    public ResponseEntity<Map<String, Object>> emsignerConfig() {
        return ResponseEntity.ok(emsignerDebugService.debugConfig());
    }
}

