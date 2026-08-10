package com.los.core.creditintelligence.decision.api;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionHistoricalReplay;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decision.repository.CiCreditRecommendationRepository;
import com.los.core.creditintelligence.decision.repository.CiDecisionStrategyRepository;
import com.los.core.creditintelligence.decision.service.AuthorityMatrixEngine;
import com.los.core.creditintelligence.decision.service.CreditDecisionViewBuilder;
import com.los.core.creditintelligence.decision.service.DecisionComparisonService;
import com.los.core.creditintelligence.decision.service.DecisionHistoricalReplayService;
import com.los.core.creditintelligence.decision.service.DecisionRecommendationExplanationBuilder;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
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
 * Internal Shadow Decision Engine APIs — never production-authoritative.
 */
@RestController
@RequestMapping("/api/internal/credit-intelligence/decision-engine")
@RequiredArgsConstructor
public class DecisionEngineAdminController {

    private final CreditIntelligenceProperties properties;
    private final ShadowDecisionEngine shadowDecisionEngine;
    private final DecisionHistoricalReplayService historicalReplayService;
    private final DecisionComparisonService comparisonService;
    private final DecisionRecommendationExplanationBuilder explanationBuilder;
    private final CreditDecisionViewBuilder decisionViewBuilder;
    private final AuthorityMatrixEngine authorityMatrixEngine;
    private final CiDecisionStrategyRepository strategyRepository;
    private final CiCreditRecommendationRepository recommendationRepository;

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
        CiDecisionStrategy strategy = requireStrategy(body, tenantId);
        DecisionRuntimeInput input = parseInput(body, tenantId);
        CiCreditRecommendation rec = shadowDecisionEngine.recommend(strategy, input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recommendationId", rec.getId());
        out.put("outcome", rec.getRecommendationOutcome());
        out.put("amount", rec.getRecommendedAmount());
        out.put("tenureMonths", rec.getRecommendedTenureMonths());
        out.put("finalRate", rec.getRecommendedFinalRate());
        out.put("authority", rec.getApprovalAuthorityLevel());
        out.put("deterministicDecisionHash", rec.getDeterministicDecisionHash());
        out.put("authoritative", false);
        out.put("shadowOnly", true);
        out.put("status", rec.getStatus());
        out.put("explanation", explanationBuilder.build(rec));
        out.put("decisionView", decisionViewBuilder.build(rec, input, Map.of()));
        out.put("dimensions", rec.getDimensions());
        return out;
    }

    @GetMapping("/recommendations/{id}")
    public Map<String, Object> getRecommendation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        CiCreditRecommendation rec = recommendationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
        if (!tenantId.equals(rec.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access rejected");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", rec.getId());
        out.put("outcome", rec.getRecommendationOutcome());
        out.put("amount", rec.getRecommendedAmount());
        out.put("authoritative", Boolean.FALSE.equals(rec.getAuthoritative()) ? false : rec.getAuthoritative());
        out.put("shadowOnly", true);
        out.put("status", rec.getStatus());
        out.put("deterministicDecisionHash", rec.getDeterministicDecisionHash());
        out.put("explanation", explanationBuilder.build(rec));
        return out;
    }

    @GetMapping("/applications/{id}/decision-recommendation")
    public Map<String, Object> applicationRecommendation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        List<CiCreditRecommendation> list = recommendationRepository.findByTenantIdAndApplicationId(tenantId, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", id);
        out.put("count", list.size());
        out.put("recommendations", list.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("outcome", r.getRecommendationOutcome());
            row.put("amount", r.getRecommendedAmount());
            row.put("hash", r.getDeterministicDecisionHash());
            row.put("authoritative", false);
            return row;
        }).toList());
        out.put("shadowOnly", true);
        return out;
    }

    @GetMapping("/applications/{id}/decision-comparison")
    public Map<String, Object> applicationComparison(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        List<CiCreditRecommendation> list = recommendationRepository.findByTenantIdAndApplicationId(tenantId, id);
        CiCreditRecommendation canonical = list.isEmpty() ? null : list.get(0);
        Map<String, Object> production = body == null ? Map.of() : asMap(body.get("production"));
        Map<String, Object> legacy = body == null ? Map.of() : asMap(body.get("legacyShadow"));
        return comparisonService.compare(production, legacy, canonical);
    }

    @GetMapping("/applications/{id}/approval-authority")
    public Map<String, Object> approvalAuthority(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        List<CiCreditRecommendation> list = recommendationRepository.findByTenantIdAndApplicationId(tenantId, id);
        if (!list.isEmpty()) {
            CiCreditRecommendation rec = list.get(0);
            Map<String, Object> out = new LinkedHashMap<>(rec.getAuthorityDetail() == null
                    ? Map.of() : rec.getAuthorityDetail());
            out.put("applicationId", id);
            out.put("shadowOnly", true);
            return out;
        }
        CiDecisionStrategy strategy = DecisionStrategyFactory.p2ValidationStrategyV1(tenantId);
        DecisionRuntimeInput input = DecisionRuntimeInput.builder()
                .tenantId(tenantId)
                .applicationId(id)
                .policyOverallOutcome("PASS")
                .scoreResult(Map.of("grade", "B"))
                .requestedAmount(body == null ? BigDecimal.valueOf(400000)
                        : bd(body.get("amount"), BigDecimal.valueOf(400000)))
                .build();
        var auth = authorityMatrixEngine.compute(
                asMap(strategy.getContent().get("authorityStrategy")),
                input, input.requestedAmount(), 0, false);
        Map<String, Object> out = new LinkedHashMap<>(auth.detail());
        out.put("applicationId", id);
        out.put("shadowOnly", true);
        out.put("fixtureStrategy", true);
        return out;
    }

    @PostMapping("/historical-replay")
    public Map<String, Object> historicalReplay(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        if (!properties.getDecisionEngine().isHistoricalReplayEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Historical replay disabled");
        }
        UUID tenantId = resolveTenant(tenantHeader);
        CiDecisionStrategy strategy = requireStrategy(body, tenantId);
        List<DecisionRuntimeInput> inputs = parseInputList(body, tenantId);
        CiDecisionHistoricalReplay replay = historicalReplayService.replay(
                strategy, inputs, str(body, "createdBy", "system"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", replay.getId());
        out.put("summary", replay.getSummary());
        out.put("shadowOnly", true);
        out.put("type", "DECISION_HISTORICAL_REPLAY");
        return out;
    }

    private void assertEnabled() {
        if (!properties.getDecisionEngine().isEnabled()
                && !properties.getDecisionEngine().isShadowEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Decision engine disabled");
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

    private CiDecisionStrategy requireStrategy(Map<String, Object> body, UUID tenantId) {
        if (body.get("strategyId") != null) {
            UUID id = UUID.fromString(String.valueOf(body.get("strategyId")));
            CiDecisionStrategy s = strategyRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Strategy not found"));
            if (!tenantId.equals(s.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access rejected");
            }
            return s;
        }
        if (Boolean.TRUE.equals(body.get("useValidationFixture"))
                || "P2_VALIDATION_STRATEGY_V1".equals(String.valueOf(body.get("strategyCode")))) {
            return DecisionStrategyFactory.p2ValidationStrategyV1(tenantId);
        }
        if (body.get("strategy") instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sm = (Map<String, Object>) raw;
            return CiDecisionStrategy.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .strategyCode(String.valueOf(sm.getOrDefault("strategyCode", "INLINE")))
                    .version(String.valueOf(sm.getOrDefault("version", "1")))
                    .status("SHADOW")
                    .content(asMap(sm.get("content")).isEmpty() ? sm : asMap(sm.get("content")))
                    .contentHash(String.valueOf(sm.getOrDefault("contentHash", "inline")))
                    .build();
        }
        return DecisionStrategyFactory.p2ValidationStrategyV1(tenantId);
    }

    @SuppressWarnings("unchecked")
    private DecisionRuntimeInput parseInput(Map<String, Object> body, UUID tenantId) {
        Map<String, Object> input = body.get("input") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;
        return DecisionRuntimeInput.builder()
                .tenantId(tenantId)
                .applicationId(uuid(input.get("applicationId")))
                .evaluationContextId(uuid(input.get("evaluationContextId")))
                .policyEvaluationId(uuid(input.get("policyEvaluationId")))
                .policyPackageId(uuid(input.get("policyPackageId")))
                .productCode(str(input, "productCode", null))
                .facts(asMap(input.get("facts")))
                .metrics(asMap(input.get("metrics")))
                .reconciliations(asMap(input.get("reconciliations")))
                .policyParameters(asMap(input.get("policyParameters")))
                .applicationFields(asMap(input.get("applicationFields")))
                .policyOverallOutcome(str(input, "policyOverallOutcome", "PASS"))
                .policyRuleResults(listOfMaps(input.get("policyRuleResults")))
                .scoreResult(asMap(input.get("scoreResult")))
                .requestedAmount(bd(input.get("requestedAmount"), null))
                .requestedTenureMonths(input.get("requestedTenureMonths") instanceof Number n
                        ? n.intValue() : null)
                .build();
    }

    private List<DecisionRuntimeInput> parseInputList(Map<String, Object> body, UUID tenantId) {
        List<DecisionRuntimeInput> out = new ArrayList<>();
        if (body.get("inputs") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) m;
                    out.add(parseInput(Map.of("input", mm), tenantId));
                }
            }
        } else {
            out.add(parseInput(body, tenantId));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object o) {
        if (!(o instanceof List<?> l)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : l) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private UUID uuid(Object o) {
        if (o == null) return null;
        return UUID.fromString(String.valueOf(o));
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private BigDecimal bd(Object o, BigDecimal def) {
        if (o == null) return def;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }
}
