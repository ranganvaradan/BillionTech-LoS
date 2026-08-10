package com.los.core.creditintelligence.validation.api;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.model.ValidationRunResult;
import com.los.core.creditintelligence.validation.service.CutoverReadinessAssessor;
import com.los.core.creditintelligence.validation.service.LegacyDefaultInventory;
import com.los.core.creditintelligence.validation.service.MultiSourceValidationHarness;
import com.los.core.creditintelligence.validation.service.PolicyAmbiguityCatalog;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import com.los.core.creditintelligence.validation.service.PolicyBindingCatalog;
import com.los.core.creditintelligence.validation.service.SecurityValidationScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal validation dashboard APIs — no raw provider payloads.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence")
@RequiredArgsConstructor
public class ValidationAdminController {

    private final MultiSourceValidationHarness harness;
    private final CreditIntelligenceProperties properties;
    private final PolicyAuthoringRegistry policyAuthoringRegistry;
    private final PolicyAmbiguityCatalog policyAmbiguityCatalog;
    private final PolicyBindingCatalog policyBindingCatalog;
    private final SecurityValidationScanner securityValidationScanner;
    private final LegacyDefaultInventory legacyDefaultInventory;
    private final CutoverReadinessAssessor cutoverReadinessAssessor;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/validation/runs")
    public Map<String, Object> listRuns(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertValidationEnabled();
        List<Map<String, Object>> runs = harness.listRuns().stream().map(this::summarizeRun).toList();
        return Map.of("runs", runs, "count", runs.size());
    }

    @GetMapping("/validation/runs/{id}")
    public Map<String, Object> getRun(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertValidationEnabled();
        ValidationRunResult run = harness.getRun(id);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Validation run not found");
        }
        return detailRun(run);
    }

    @PostMapping("/validation/runs")
    public Map<String, Object> startRun(
            @RequestParam String caseCode,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertValidationEnabled();
        ValidationCaseCode code = ValidationCaseCode.valueOf(caseCode);
        UUID tenantId = tenantHeader != null && !tenantHeader.isBlank()
                ? UUID.fromString(tenantHeader.trim())
                : properties.getDefaultTenantId();
        ValidationRunResult result = harness.runCase(code, tenantId, UUID.randomUUID());
        return summarizeRun(result);
    }

    @GetMapping("/applications/{id}/credit-evidence")
    public Map<String, Object> creditEvidence(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        List<ValidationRunResult> runs = harness.listByApplication(id);
        if (runs.isEmpty()) {
            // Non-authoritative: run CASE_A as illustration only when validation enabled
            if (!harness.isEnabled()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No credit evidence validation run for application");
            }
            ValidationRunResult created = harness.runCase(
                    ValidationCaseCode.CASE_A_STRONG, properties.getDefaultTenantId(), id);
            return stripRaw(created.evidenceView());
        }
        return stripRaw(runs.get(runs.size() - 1).evidenceView());
    }

    @GetMapping("/applications/{id}/policy-comparison")
    public Map<String, Object> policyComparison(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        List<ValidationRunResult> runs = harness.listByApplication(id);
        if (runs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No policy comparison for application");
        }
        return stripRaw(runs.get(runs.size() - 1).dualPolicyComparison());
    }

    @GetMapping("/applications/{id}/cutover-readiness")
    public Map<String, Object> cutoverReadiness(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        List<ValidationRunResult> runs = harness.listByApplication(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", id.toString());
        if (runs.isEmpty()) {
            var assessment = cutoverReadinessAssessor.assess(
                    properties.getDefaultTenantId(),
                    false, null, null,
                    legacyDefaultInventory.inventory().size(),
                    false, false, false, 0, false);
            out.put("outcome", assessment.outcome().name());
            out.put("blockers", assessment.blockers());
            out.put("dimensions", assessment.dimensions());
            return out;
        }
        ValidationRunResult latest = runs.get(runs.size() - 1);
        out.put("outcome", latest.cutoverOutcome().name());
        out.put("blockers", latest.blockers());
        out.put("dimensions", latest.cutoverDimensions());
        out.put("validationRunId", latest.runId().toString());
        return out;
    }

    @GetMapping("/validation/policy-authoring-registry")
    public Map<String, Object> policyAuthoringRegistry(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return policyAuthoringRegistry.registry();
    }

    @GetMapping("/validation/policy-ambiguity-catalog")
    public Map<String, Object> policyAmbiguityCatalog(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return policyAmbiguityCatalog.catalog();
    }

    @GetMapping("/validation/policy-bindings")
    public Map<String, Object> policyBindings(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        UUID tenantId = properties.getDefaultTenantId();
        return policyBindingCatalog.coverageSummary(tenantId);
    }

    @GetMapping("/validation/security-scan")
    public Map<String, Object> securityScan(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return securityValidationScanner.toMap(securityValidationScanner.scan());
    }

    private Map<String, Object> summarizeRun(ValidationRunResult run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", run.runId().toString());
        m.put("runCode", run.runCode());
        m.put("caseCode", run.caseCode().name());
        m.put("dataOrigin", run.dataOrigin().name());
        m.put("replayIdentical", run.replayIdentical());
        m.put("cutoverOutcome", run.cutoverOutcome().name());
        m.put("deterministicEvaluationHash", run.deterministicEvaluationHash());
        m.put("durationMs", run.totalDurationMs());
        m.put("applicationId", run.applicationId().toString());
        // no raw payloads
        return m;
    }

    private Map<String, Object> detailRun(ValidationRunResult run) {
        Map<String, Object> m = summarizeRun(run);
        m.put("summary", run.summary());
        m.put("coverage", run.coverage());
        m.put("reconciliations", run.reconciliations());
        m.put("dualPolicyComparison", run.dualPolicyComparison());
        m.put("evidenceView", stripRaw(run.evidenceView()));
        m.put("obligationMatches", run.obligationMatches());
        m.put("blockers", run.blockers());
        m.put("stageDurationsMs", run.stageDurationsMs());
        m.put("providerStack", run.providerStackResults().stream().map(p -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("provider", p.provider());
            r.put("sourceType", p.sourceType());
            r.put("origin", p.origin().name());
            r.put("parserVersion", p.parserVersion());
            r.put("normalizerVersion", p.normalizerVersion());
            r.put("entityCount", p.entityCount());
            r.put("factCount", p.factCount());
            r.put("errors", p.errors());
            r.put("warnings", p.warnings());
            r.put("ignoredHighValueFields", p.ignoredHighValueFields());
            return r;
        }).toList());
        return m;
    }

    private static Map<String, Object> stripRaw(Map<String, Object> view) {
        if (view == null) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(view);
        copy.remove("rawPayload");
        copy.remove("rawPayloads");
        copy.remove("providerPayload");
        return copy;
    }

    private void assertValidationEnabled() {
        if (!harness.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "credit-intelligence.validation.enabled=false");
        }
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token blank — allowing validation API (local)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
