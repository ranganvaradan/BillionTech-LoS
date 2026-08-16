package com.los.core.creditintelligence.policystudio.api;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.graph.PolicyGraphPolicyTestService;
import com.los.core.creditintelligence.policystudio.graph.PolicyRuleGraphMaterializer;
import com.los.core.creditintelligence.policystudio.graph.PolicyRuleGraphService;
import com.los.core.creditintelligence.policystudio.graph.PolicyToLegacyUwCompiler;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine;
import com.los.core.service.underwriting.ScorecardCanonicalFactorMapper;
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
import java.util.Set;
import java.util.UUID;

/**
 * DP-3 Policy rule graph / inventory / Policy↔Scorecard APIs.
 * Never activates production underwriting authority.
 */
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/dp3")
@RequiredArgsConstructor
public class Dp3PolicyGraphAdminController {

    private final PolicyRuleGraphMaterializer materializer;
    private final PolicyRuleGraphService graphService;
    private final PolicyGraphPolicyTestService policyTestService;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final CreditIntelligenceProperties properties;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @PostMapping("/policies/{id}/materialize-graph")
    public Map<String, Object> materialize(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        return materializer.materializeDocument(id);
    }

    @PostMapping("/materialize-all-graphs")
    public Map<String, Object> materializeAll(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        return materializer.materializeAll();
    }

    @GetMapping("/policies/{id}/parameter-inventory")
    public Map<String, Object> inventory(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        graphService.ensureMaterialized(id);
        return graphService.parameterInventory(id);
    }

    @GetMapping("/policies/{id}/rule-graph")
    public Map<String, Object> ruleGraph(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        graphService.ensureMaterialized(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyDocumentId", id);
        out.put("rules", graphService.loadPersistedAstRules(id));
        graphService.latestGraph(id).ifPresent(g -> {
            out.put("graphId", g.getId());
            out.put("graphHash", g.getGraphHash());
            out.put("unresolvedOperandCount", g.getUnresolvedOperandCount());
            out.put("immutable", g.isImmutable());
        });
        return out;
    }

    @PostMapping("/policies/{id}/policy-test")
    public Map<String, Object> policyTest(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        assertToken(token);
        graphService.ensureMaterialized(id);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = body != null && body.get("metrics") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = body != null && body.get("facts") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        java.time.LocalDate evaluationAsOf = null;
        if (body != null && body.get("evaluationAsOf") != null) {
            evaluationAsOf = java.time.LocalDate.parse(String.valueOf(body.get("evaluationAsOf")).trim());
        }
        return policyTestService.run(id, metrics, facts, evaluationAsOf, null);
    }

    @PostMapping("/policies/{id}/link-scorecard/{scorecardId}")
    public Map<String, Object> linkScorecard(
            @PathVariable UUID id,
            @PathVariable UUID scorecardId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        UnderwritingScorecard card = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "scorecard"));
        card.setPolicyDocumentId(id);
        if (card.getScoringMode() == null || "LEGACY_POINTS_V1".equals(card.getScoringMode())) {
            // New Policy-linked scorecards prefer V2; do not mutate unrelated legacy cards unless linked.
            card.setScoringMode(PolicyWeightedScorecardEngine.MODE);
        }
        scorecardRepository.save(card);
        return graphService.linkScorecard(id, scorecardId);
    }

    @GetMapping("/policies/{id}/scorecard-factor-picker")
    public Map<String, Object> factorPicker(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        graphService.ensureMaterialized(id);
        Map<String, Object> inv = graphService.parameterInventory(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyDocumentId", id);
        out.put("factors", inv.get("scoringEligibleParameters"));
        out.put("rule", "Scorecard may use ONLY parameters that belong to this Policy Version");
        out.put("gacatBrowseForbidden", true);
        return out;
    }

    @PostMapping("/policies/{id}/scorecard-factors/validate")
    public Map<String, Object> validateFactors(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertToken(token);
        graphService.ensureMaterialized(id);
        Set<String> allowed = graphService.policyCanonicalParameterIds(id);
        @SuppressWarnings("unchecked")
        List<String> factors = body.get("canonicalParameterIds") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        try {
            PolicyWeightedScorecardEngine.assertFactorsSubsetOfPolicy(allowed, factors);
            return Map.of("valid", true, "allowed", allowed, "requested", factors);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/scorecards/weight-preview")
    public Map<String, Object> weightPreview(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertToken(token);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawFactors = body.get("factors") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        List<PolicyWeightedScorecardEngine.FactorInput> inputs = new ArrayList<>();
        for (Map<String, Object> f : rawFactors) {
            String state = String.valueOf(f.getOrDefault("dataState", "PRESENT"));
            inputs.add(new PolicyWeightedScorecardEngine.FactorInput(
                    String.valueOf(f.get("canonicalParameterId")),
                    new BigDecimal(String.valueOf(f.getOrDefault("rawWeight", "0"))),
                    Boolean.TRUE.equals(f.get("required")),
                    PolicyWeightedScorecardEngine.FactorDataState.valueOf(state),
                    f.get("bandPointsEarned") != null ? new BigDecimal(String.valueOf(f.get("bandPointsEarned"))) : BigDecimal.valueOf(100),
                    f.get("bandPointsMax") != null ? new BigDecimal(String.valueOf(f.get("bandPointsMax"))) : BigDecimal.valueOf(100)
            ));
        }
        var result = PolicyWeightedScorecardEngine.score(inputs);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", PolicyWeightedScorecardEngine.MODE);
        out.put("valid", result.valid());
        out.put("dataInsufficient", result.dataInsufficient());
        out.put("outcome", result.outcome());
        out.put("weightedScore", result.weightedScore());
        out.put("weights", result.weights());
        out.put("errors", result.errors());
        out.put("rawWeightsMustTotal100", false);
        return out;
    }

    @GetMapping("/scorecards/legacy-factor-diagnostics")
    public Map<String, Object> legacyDiagnostics(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        // Diagnostic inventory only — no automatic conversion.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("autoConvert", false);
        out.put("diagnostics", ScorecardCanonicalFactorMapper.inventoryKeys(List.of(
                "BUREAU_SCORE", "AVERAGE_BANK_BALANCE", "ANNUAL_GST_TURNOVER", "DSCR", "LTV", "BUREAU_ENQUIRIES_3M")));
        out.put("note", "Legacy keys remain for LEGACY_POINTS_V1 scorecards; V2 requires exact GACAT IDs");
        return out;
    }

    @GetMapping("/policies/{id}/legacy-uw-compile-plan")
    public Map<String, Object> compilePlan(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        graphService.ensureMaterialized(id);
        return PolicyToLegacyUwCompiler.compilePlan(graphService.loadPersistedAstRules(id));
    }

    @PostMapping("/policies/{id}/mark-graph-immutable")
    public Map<String, Object> markImmutable(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        return graphService.markImmutable(id);
    }

    private void assertToken(String token) {
        if (internalToken == null || internalToken.isBlank()) return;
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
