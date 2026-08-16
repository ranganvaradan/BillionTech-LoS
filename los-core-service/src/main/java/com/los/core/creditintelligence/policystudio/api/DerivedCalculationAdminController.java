package com.los.core.creditintelligence.policystudio.api;

import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationResearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data & Parameters — research / approve / define / test safe derived calculations.
 * Research is advisory; Accept creates a V139 definition for an existing canonical id.
 */
@RestController
@RequestMapping("/api/v1/data-parameters/derived-calculations")
@RequiredArgsConstructor
public class DerivedCalculationAdminController {

    private final DerivedCalculationDefinitionService service;
    private final DerivedCalculationResearchService researchService;

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

    @PostMapping("/research/suggest")
    public Map<String, Object> suggest(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false, defaultValue = "admin") String actor) {
        String target = body.get("targetParameterId") == null && body.get("canonicalParameterId") == null
                ? ""
                : String.valueOf(body.getOrDefault("targetParameterId",
                body.getOrDefault("canonicalParameterId", ""))).trim();
        return researchService.suggest(target, tenantId, actor);
    }

    @GetMapping("/research/proposals/{id}")
    public Map<String, Object> getProposal(@PathVariable UUID id) {
        return researchService.getProposal(id);
    }

    @GetMapping("/research/by-parameter/{canonicalParameterId:.+}")
    public List<Map<String, Object>> listProposals(@PathVariable String canonicalParameterId) {
        return researchService.listForTarget(canonicalParameterId);
    }

    @PostMapping("/research/proposals/{id}/edit")
    public Map<String, Object> editProposal(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false, defaultValue = "admin") String actor) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expression = body.get("expression") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : body.get("proposedExpression") instanceof Map<?, ?> m2
                ? (Map<String, Object>) m2
                : Map.of();
        return researchService.updateProposalExpression(id, expression, actor);
    }

    @PostMapping("/research/proposals/{id}/accept")
    public Map<String, Object> accept(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "admin") String actor) {
        return researchService.accept(id, actor);
    }

    @PostMapping("/research/proposals/{id}/reject")
    public Map<String, Object> reject(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "admin") String actor) {
        return researchService.reject(id, actor);
    }
}
