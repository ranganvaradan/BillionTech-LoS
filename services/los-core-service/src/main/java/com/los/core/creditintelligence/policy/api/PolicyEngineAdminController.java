package com.los.core.creditintelligence.policy.api;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.CiPolicyHistoricalReplay;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.repository.CiExecutablePolicyPackageRepository;
import com.los.core.creditintelligence.policy.repository.CiPolicyEvaluationRepository;
import com.los.core.creditintelligence.policy.service.PolicyDecisionExplanationBuilder;
import com.los.core.creditintelligence.policy.service.PolicyHistoricalReplayService;
import com.los.core.creditintelligence.policy.service.PolicyPackagePublisher;
import com.los.core.creditintelligence.policy.service.PolicyReplayService;
import com.los.core.creditintelligence.policy.service.PolicySimulationService;
import com.los.core.creditintelligence.policy.service.ShadowPolicyEngine;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDraftPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal Shadow Policy Engine APIs — never production-active.
 */
@RestController
@RequestMapping("/api/internal/credit-intelligence/policy-engine")
@RequiredArgsConstructor
public class PolicyEngineAdminController {

    private final CreditIntelligenceProperties properties;
    private final ShadowPolicyEngine shadowPolicyEngine;
    private final PolicySimulationService simulationService;
    private final PolicyReplayService replayService;
    private final PolicyHistoricalReplayService historicalReplayService;
    private final PolicyPackagePublisher publisher;
    private final PolicyDecisionExplanationBuilder explanationBuilder;
    private final CiExecutablePolicyPackageRepository packageRepository;
    private final CiPolicyEvaluationRepository evaluationRepository;
    private final CiPolicyDraftPackageRepository draftPackageRepository;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @PostMapping("/simulate")
    public Map<String, Object> simulate(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        CiExecutablePolicyPackage pkg = requirePackage(body, tenantId);
        PolicyEvaluationInput input = parseInput(body, tenantId);
        return simulationService.simulate(pkg, input);
    }

    @PostMapping("/replay/{evaluationContextId}")
    public Map<String, Object> replay(
            @PathVariable UUID evaluationContextId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        CiExecutablePolicyPackage pkg = requirePackage(body, tenantId);
        PolicyEvaluationInput input = parseInput(body, tenantId);
        return replayService.replay(pkg, input, evaluationContextId);
    }

    @PostMapping("/historical-replay")
    public Map<String, Object> historicalReplay(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        if (!properties.getPolicyEngine().isHistoricalReplayEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Historical replay disabled");
        }
        UUID tenantId = resolveTenant(tenantHeader);
        CiExecutablePolicyPackage pkg = requirePackage(body, tenantId);
        List<PolicyEvaluationInput> inputs = parseInputList(body, tenantId);
        CiPolicyHistoricalReplay replay = historicalReplayService.replay(
                pkg, inputs, str(body, "createdBy", "system"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", replay.getId());
        out.put("summary", replay.getSummary());
        out.put("shadowOnly", true);
        return out;
    }

    @GetMapping("/packages/{id}")
    public Map<String, Object> getPackage(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        CiExecutablePolicyPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"));
        if (!tenantId.equals(pkg.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access rejected");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", pkg.getId());
        out.put("policyCode", pkg.getPolicyCode());
        out.put("version", pkg.getVersion());
        out.put("status", pkg.getStatus());
        out.put("contentHash", pkg.getContentHash());
        out.put("shadowOnly", true);
        out.put("productionActive", false);
        out.put("activationForbidden", true);
        return out;
    }

    @PostMapping("/packages/{draftPackageId}/publish-shadow")
    public Map<String, Object> publishShadow(
            @PathVariable UUID draftPackageId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        CiPolicyDraftPackage draft = draftPackageRepository.findById(draftPackageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        if (!tenantId.equals(draft.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access rejected");
        }
        CiExecutablePolicyPackage published = publisher.fromDraft(draft);
        if (!"SHADOW".equals(published.getStatus())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Publish must be SHADOW only");
        }
        published = packageRepository.save(published);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", published.getId());
        out.put("status", published.getStatus());
        out.put("contentHash", published.getContentHash());
        out.put("testSuiteHash", published.getTestSuiteHash());
        out.put("shadowOnly", true);
        out.put("neverActive", true);
        return out;
    }

    @GetMapping("/evaluations/{id}/explanation")
    public Map<String, Object> explanation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertEnabled();
        resolveTenant(tenantHeader);
        CiPolicyEvaluation eval = evaluationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation not found"));
        return explanationBuilder.build(eval);
    }

    private void assertEnabled() {
        if (!properties.getPolicyEngine().isEnabled() && !properties.getPolicyEngine().isDslShadowEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Policy engine disabled");
        }
    }

    private UUID resolveTenant(String tenantHeader) {
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return UUID.fromString(tenantHeader.trim());
        }
        return properties.getDefaultTenantId();
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }

    private CiExecutablePolicyPackage requirePackage(Map<String, Object> body, UUID tenantId) {
        if (body.get("packageId") != null) {
            UUID id = UUID.fromString(String.valueOf(body.get("packageId")));
            CiExecutablePolicyPackage pkg = packageRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"));
            if (!tenantId.equals(pkg.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access rejected");
            }
            return pkg;
        }
        if (body.get("package") instanceof Map<?, ?> rawPkg) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pm = (Map<String, Object>) rawPkg;
            @SuppressWarnings("unchecked")
            Map<String, Object> content = pm.get("content") instanceof Map<?, ?> cm
                    ? (Map<String, Object>) cm : Map.of();
            return CiExecutablePolicyPackage.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .policyCode(String.valueOf(pm.getOrDefault("policyCode", "INLINE")))
                    .version(String.valueOf(pm.getOrDefault("version", "1")))
                    .status("SHADOW")
                    .content(content)
                    .build();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "packageId or package required");
    }

    @SuppressWarnings("unchecked")
    private PolicyEvaluationInput parseInput(Map<String, Object> body, UUID tenantId) {
        Map<String, Object> input = body.get("input") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;
        UUID ctxId = input.get("evaluationContextId") == null
                ? UUID.randomUUID()
                : UUID.fromString(String.valueOf(input.get("evaluationContextId")));
        return new PolicyEvaluationInput(
                tenantId,
                ctxId,
                mapOrEmpty(input, "facts"),
                mapOrEmpty(input, "metrics"),
                mapOrEmpty(input, "reconciliations"),
                mapOrEmpty(input, "policyParameters"),
                mapOrEmpty(input, "applicationFields"),
                null,
                mapOrEmpty(input, "metadata"));
    }

    @SuppressWarnings("unchecked")
    private List<PolicyEvaluationInput> parseInputList(Map<String, Object> body, UUID tenantId) {
        List<PolicyEvaluationInput> out = new ArrayList<>();
        if (!(body.get("inputs") instanceof List<?> list)) {
            out.add(parseInput(body, tenantId));
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add(parseInput(Map.of("input", m), tenantId));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrEmpty(Map<String, Object> source, String key) {
        if (source.get(key) instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null ? def : String.valueOf(v);
    }
}
