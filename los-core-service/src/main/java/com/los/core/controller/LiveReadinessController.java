package com.los.core.controller;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityParityService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.service.readiness.CustomerGoLiveReadinessValidator;
import com.los.core.service.readiness.DataParametersAdminService;
import com.los.core.service.readiness.PolicyRequiredParameterExtractor;
import com.los.core.service.readiness.ProductConfigurationComposeService;
import com.los.core.service.readiness.WorkflowParameterProvidesCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * LOS-LIVE-READINESS-1 — Admin read APIs for Data & Parameters, readiness, product compose.
 * Does not change production underwriting authority.
 */
@RestController
@RequestMapping("/api/v1/admin/live-readiness")
@RequiredArgsConstructor
@Tag(name = "Live Readiness", description = "Data & Parameters + Product/Workflow/Policy readiness (read-only)")
public class LiveReadinessController {

    private final DataParametersAdminService dataParametersAdminService;
    private final ProductConfigurationComposeService composeService;
    private final PolicyRequiredParameterExtractor parameterExtractor;
    private final CustomerGoLiveReadinessValidator customerGoLiveReadinessValidator;

    @Autowired(required = false)
    private PolicyStudioOrchestrator policyStudioOrchestrator;

    @Autowired(required = false)
    private CanonicalParameterCapabilityParityService parameterCapabilityParityService;

    @GetMapping("/data-parameters")
    @Operation(summary = "Administration → Data & Parameters overview")
    public ResponseEntity<Map<String, Object>> dataParameters() {
        return ResponseEntity.ok(dataParametersAdminService.overview());
    }

    @GetMapping("/parameter-capability-parity")
    @Operation(summary = "Single-parameter truth: D&P vs Policy Studio vs spine execution capability")
    public ResponseEntity<Map<String, Object>> parameterCapabilityParity() {
        if (parameterCapabilityParityService == null) {
            return ResponseEntity.ok(Map.of(
                    "parityPass", false,
                    "message", "CanonicalParameterCapabilityParityService unavailable"));
        }
        return ResponseEntity.ok(parameterCapabilityParityService.runParityCheck());
    }

    @GetMapping("/data-parameters/by-source")
    public ResponseEntity<Map<String, Object>> bySource(@RequestParam String source) {
        return ResponseEntity.ok(dataParametersAdminService.browseBySource(source));
    }

    @GetMapping("/data-parameters/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(dataParametersAdminService.search(q));
    }

    @GetMapping("/data-parameters/{parameterId}")
    public ResponseEntity<Map<String, Object>> parameter(@PathVariable String parameterId) {
        return ResponseEntity.ok(dataParametersAdminService.parameterDetail(parameterId));
    }

    @GetMapping("/workflow-provides")
    @Operation(summary = "Workflow step → CanonicalParameterRegistry provides mapping")
    public ResponseEntity<Map<String, Object>> workflowProvides() {
        return ResponseEntity.ok(WorkflowParameterProvidesCatalog.catalogueView());
    }

    @GetMapping("/product-configuration/options")
    @Operation(summary = "Selectable existing Workflow / Live Rules / Scorecards for compose")
    public ResponseEntity<Map<String, Object>> productOptions() {
        return ResponseEntity.ok(composeService.options());
    }

    @PostMapping("/product-configuration/compose")
    @Operation(summary = "Compose Product→Workflow→Policy→Scorecard and return readiness")
    public ResponseEntity<Map<String, Object>> compose(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(composeService.compose(body == null ? Map.of() : body));
    }

    @GetMapping("/product-configuration/golden")
    @Operation(summary = "Staging-safe golden Company Term Loan composition + readiness")
    public ResponseEntity<Map<String, Object>> golden() {
        return ResponseEntity.ok(composeService.goldenCompose());
    }

    @GetMapping("/policy-studio/{documentId}/required-parameters")
    @Operation(summary = "Extract required parameters from a Policy Studio session")
    public ResponseEntity<Map<String, Object>> studioRequired(@PathVariable UUID documentId) {
        if (policyStudioOrchestrator == null) {
            return ResponseEntity.ok(Map.of(
                    "requiredParameterIds", java.util.List.of(),
                    "message", "Policy Studio orchestrator unavailable",
                    "allowCanonicalAuthority", false));
        }
        var session = policyStudioOrchestrator.requireSession(documentId);
        return ResponseEntity.ok(parameterExtractor.fromStudioSession(session));
    }

    @PostMapping("/customer-go-live")
    @Operation(summary = "SAFE TO GO LIVE? YES/NO for tenant/product (read-model; no auto-deploy)")
    public ResponseEntity<Map<String, Object>> customerGoLive(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(customerGoLiveReadinessValidator.assess(body == null ? Map.of() : body));
    }
}
