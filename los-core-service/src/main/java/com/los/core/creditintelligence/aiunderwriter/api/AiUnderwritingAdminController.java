package com.los.core.creditintelligence.aiunderwriter.api;

import com.los.core.creditintelligence.aiunderwriter.domain.AiScenarioRequest;
import com.los.core.creditintelligence.aiunderwriter.service.AiOutputComparisonService;
import com.los.core.creditintelligence.aiunderwriter.service.AiReviewService;
import com.los.core.creditintelligence.aiunderwriter.service.AiScenarioService;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwriterViewBuilder;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwritingService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal assistive AI underwriting APIs — never production-authoritative.
 * New flow never uses DEMO_AI_LOS_URL.
 */
@RestController
@RequestMapping("/api/internal/credit-intelligence/ai-underwriting")
@RequiredArgsConstructor
public class AiUnderwritingAdminController {

    private final CreditIntelligenceProperties properties;
    private final AiUnderwritingService underwritingService;
    private final AiReviewService reviewService;
    private final AiScenarioService scenarioService;
    private final AiUnderwriterViewBuilder viewBuilder;
    private final AiOutputComparisonService comparisonService;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @PostMapping("/applications/{id}/ai-underwriting")
    public Map<String, Object> analyzeForApplication(
            @PathVariable("id") UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        UUID tenantId = resolveTenant(tenantHeader);
        if (!underwritingService.isEnabledForTenant(tenantId)) {
            return unavailable();
        }
        return underwritingService.analyze(
                tenantId,
                applicationId,
                uuid(body.get("evaluationContextId")),
                uuid(body.get("policyEvaluationId")),
                uuid(body.get("recommendationId")),
                stringList(body.get("requestedOutputTypes")),
                asMap(body.get("creditEvidenceView")),
                asMap(body.get("policyDecisionExplanation")),
                asMap(body.get("creditDecisionView")),
                asMap(body.get("shadowRecommendation")),
                listOfMaps(body.get("investigationQuestions")),
                str(body, "createdBy", "system"),
                asMap(body.get("shadowRecommendation"))
        );
    }

    @GetMapping("/applications/{id}/ai-underwriting")
    public Map<String, Object> listForApplication(
            @PathVariable("id") UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        UUID tenantId = resolveTenant(tenantHeader);
        Map<String, Object> list = underwritingService.listForApplication(tenantId, applicationId);
        list.put("aiUnderwriterView", viewBuilder.build(tenantId, applicationId));
        return list;
    }

    @GetMapping("/{analysisId}")
    public Map<String, Object> getAnalysis(
            @PathVariable UUID analysisId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        return underwritingService.getAnalysis(resolveTenant(tenantHeader), analysisId);
    }

    @PostMapping("/{id}/review")
    public Map<String, Object> review(
            @PathVariable("id") UUID suggestionId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        UUID tenantId = resolveTenant(tenantHeader);
        if (!underwritingService.isEnabledForTenant(tenantId)) {
            return unavailable();
        }
        return reviewService.review(
                tenantId,
                suggestionId,
                str(body, "action", null),
                str(body, "feedbackCode", null),
                str(body, "editedContent", null),
                str(body, "reviewer", "underwriter"),
                str(body, "reason", null)
        );
    }

    @PostMapping("/{id}/scenario")
    public Map<String, Object> scenario(
            @PathVariable("id") UUID analysisOrAppId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        UUID tenantId = resolveTenant(tenantHeader);
        if (!underwritingService.isEnabledForTenant(tenantId)
                || !properties.getAiUnderwriter().isScenarioEnabled()) {
            return unavailable();
        }
        UUID applicationId = uuid(body.get("applicationId"));
        if (applicationId == null) {
            applicationId = analysisOrAppId;
        }
        AiScenarioRequest req = new AiScenarioRequest(
                tenantId,
                applicationId,
                uuid(body.get("analysisRequestId")),
                bd(body.get("loanAmount")),
                body.get("tenureMonths") instanceof Number n ? n.intValue() : null,
                asMap(body.get("collateral")),
                asMap(body.get("canonicalRecommendation")),
                asMap(body.get("decisionInputOverrides"))
        );
        DecisionRuntimeInput input = parseInput(body, tenantId, applicationId);
        return scenarioService.runScenario(
                req,
                input,
                DecisionStrategyFactory.p2ValidationStrategyV1(tenantId),
                asMap(body.get("canonicalRecommendation"))
        );
    }

    @PostMapping("/compare")
    public Map<String, Object> compare(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        return comparisonService.compare(
                resolveTenant(tenantHeader),
                uuid(body.get("oldSuggestionId")),
                uuid(body.get("newSuggestionId"))
        );
    }

    private Map<String, Object> unavailable() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "AI_ASSISTANCE_UNAVAILABLE");
        out.put("failureCode", "AI_ASSISTANCE_UNAVAILABLE");
        out.put("authoritative", false);
        out.put("demoUrlUsed", false);
        out.put("suggestions", List.of());
        return out;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }

    private UUID resolveTenant(String tenantHeader) {
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return UUID.fromString(tenantHeader.trim());
        }
        return properties.getDefaultTenantId();
    }

    @SuppressWarnings("unchecked")
    private DecisionRuntimeInput parseInput(Map<String, Object> body, UUID tenantId, UUID applicationId) {
        Map<String, Object> input = body.get("input") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;
        return DecisionRuntimeInput.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .evaluationContextId(uuid(input.get("evaluationContextId")))
                .policyEvaluationId(uuid(input.get("policyEvaluationId")))
                .policyOverallOutcome(str(input, "policyOverallOutcome", "PASS"))
                .facts(asMap(input.get("facts")))
                .metrics(asMap(input.get("metrics")))
                .reconciliations(asMap(input.get("reconciliations")))
                .scoreResult(asMap(input.get("scoreResult")).isEmpty()
                        ? Map.of("grade", "B", "score", 720) : asMap(input.get("scoreResult")))
                .requestedAmount(bd(input.get("requestedAmount")) == null
                        ? new BigDecimal("1000000") : bd(input.get("requestedAmount")))
                .requestedTenureMonths(input.get("requestedTenureMonths") instanceof Number n
                        ? n.intValue() : 24)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object o) {
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : l) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private List<String> stringList(Object o) {
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : l) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    private UUID uuid(Object o) {
        if (o == null) {
            return null;
        }
        return UUID.fromString(String.valueOf(o));
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private BigDecimal bd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
