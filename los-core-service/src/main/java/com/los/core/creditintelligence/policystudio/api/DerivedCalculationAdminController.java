package com.los.core.creditintelligence.policystudio.api;

import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import lombok.RequiredArgsConstructor;
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
 * Data & Parameters — define / test / activate safe derived calculations.
 */
@RestController
@RequestMapping("/api/v1/data-parameters/derived-calculations")
@RequiredArgsConstructor
public class DerivedCalculationAdminController {

    private final DerivedCalculationDefinitionService service;

    @GetMapping("/by-parameter/{canonicalParameterId:.+}")
    public Map<String, Object> latest(
            @PathVariable String canonicalParameterId,
            @RequestParam(required = false) UUID tenantId) {
        return service.latestFor(canonicalParameterId, tenantId)
                .map(service::toView)
                .orElse(Map.of(
                        "found", false,
                        "canonicalParameterId", canonicalParameterId,
                        "arbitraryCodeAllowed", false));
    }

    @PostMapping
    public Map<String, Object> saveDraft(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false, defaultValue = "admin") String actor) {
        return service.saveDraft(body, tenantId, actor);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> sampleInputs) {
        return service.testWithSample(id, sampleInputs == null ? Map.of() : sampleInputs);
    }

    @PostMapping("/{id}/mark-production-ready")
    public Map<String, Object> markProductionReady(@PathVariable UUID id) {
        return service.markProductionReady(id);
    }
}
