package com.los.core.controller;

import com.los.core.model.dto.response.EsignRequestView;
import com.los.core.service.esign.EsignEmailResendService;
import com.los.core.service.esign.EsignRequestTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/esign-requests")
@RequiredArgsConstructor
@Tag(name = "eSign", description = "Per-application eSign attempts and status")
public class ApplicationEsignController {

    private final EsignRequestTrackingService esignRequestTrackingService;
    private final EsignEmailResendService esignEmailResendService;

    @GetMapping
    @Operation(summary = "List eSign requests (newest first)")
    public ResponseEntity<List<EsignRequestView>> list(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(esignRequestTrackingService.listForApplication(applicationId));
    }

    @PostMapping("/resend-link")
    @Operation(summary = "Resend existing active signing link email (no new provider URL generation)")
    public ResponseEntity<java.util.Map<String, Object>> resendLink(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(esignEmailResendService.resendSigningLink(applicationId));
    }
}
