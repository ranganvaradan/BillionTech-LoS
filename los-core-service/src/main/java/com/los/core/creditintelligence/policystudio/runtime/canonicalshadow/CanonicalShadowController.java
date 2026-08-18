package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only inspect + controlled Internal shadow run.
 * Never underwrites. Never changes credit_decision / status / CAM / sanction.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence")
@RequiredArgsConstructor
public class CanonicalShadowController {

    private final LoanApplicationRepository applicationRepository;
    private final CanonicalShadowUnderwritingService shadowService;
    private final CanonicalShadowEvaluationRepository evaluationRepository;
    private final CanonicalShadowComparisonRepository comparisonRepository;
    private final CreditIntelligenceProperties properties;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/canonical-shadow")
    public Map<String, Object> inspect(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        LoanApplication app = requireApp(applicationId);
        return inspectMap(app, null);
    }

    @PostMapping("/applications/{applicationId}/canonical-shadow/run")
    public Map<String, Object> runControlled(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        LoanApplication app = requireApp(applicationId);
        String decisionBefore = app.getCreditDecision();
        String statusBefore = app.getStatus() == null ? null : app.getStatus().name();
        CanonicalShadowEvaluationEntity row;
        try {
            row = shadowService.runControlled(app);
        } catch (Exception ex) {
            log.warn("controlled canonical shadow failed for {}: {}", applicationId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Canonical shadow failed without mutating live decision: " + ex.getMessage());
        }
        if (!java.util.Objects.equals(decisionBefore, app.getCreditDecision())
                || !java.util.Objects.equals(statusBefore, app.getStatus() == null ? null : app.getStatus().name())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Canonical shadow must not mutate live decision or status");
        }
        return inspectMap(app, row);
    }

    private Map<String, Object> inspectMap(LoanApplication app, CanonicalShadowEvaluationEntity preferred) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", app.getId().toString());
        out.put("applicationNumber", app.getApplicationNumber());
        out.put("status", app.getStatus() == null ? null : app.getStatus().name());
        out.put("creditDecision", app.getCreditDecision());
        out.put("liveDecisionUnchanged", true);
        out.put("canonicalRuntimeUsedForLiveDecision", false);
        out.put("service", CanonicalShadowUnderwritingService.SERVICE);
        out.put("aggregationAuthority", CanonicalShadowUnderwritingService.AGGREGATION_AUTHORITY);
        CreditIntelligenceProperties.CanonicalShadow cfg = properties.getCanonicalShadow();
        out.put("mode", cfg == null ? CanonicalShadowMode.LEGACY_ONLY.name() : cfg.getMode().name());
        out.put("enabledForApplication", shadowService.isEnabledFor(app));
        List<CanonicalShadowEvaluationEntity> evals =
                evaluationRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId());
        CanonicalShadowEvaluationEntity row = preferred != null ? preferred
                : (evals.isEmpty() ? null : evals.get(0));
        out.put("shadowEvaluationCount", evals.size());
        if (row == null) {
            out.put("shadowPresent", false);
            return out;
        }
        out.put("shadowPresent", true);
        out.put("shadowEvaluationId", row.getId().toString());
        out.put("shadowStatus", row.getStatus());
        out.put("canonicalDecision", row.getCanonicalDecision());
        out.put("reasonCodes", row.getReasonCodes());
        out.put("identityHash", row.getIdentityHash());
        out.put("freezeRowId", row.getFreezeRowId() == null ? null : row.getFreezeRowId().toString());
        out.put("parameterEvidence", row.getParameterEvidence());
        out.put("ruleEvidence", row.getRuleEvidence());
        out.put("scorecardEvidence", row.getScorecardEvidence());
        out.put("policyResult", row.getPolicyResult());
        out.put("aggregation", row.getAggregation());
        comparisonRepository.findFirstByShadowEvaluationId(row.getId()).ifPresent(cmp -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("comparisonId", cmp.getId().toString());
            c.put("comparisonStatus", cmp.getComparisonStatus());
            c.put("identityHash", cmp.getIdentityHash());
            c.put("legacyDecisionAt", cmp.getLegacyDecisionAt() == null ? null : cmp.getLegacyDecisionAt().toString());
            c.put("canonicalShadowAt", cmp.getCanonicalShadowAt() == null ? null : cmp.getCanonicalShadowAt().toString());
            c.put("mismatchCounts", cmp.getMismatchCounts());
            c.put("mismatches", cmp.getMismatches());
            c.put("policyTestEquivalence", cmp.getPolicyTestEquivalence());
            out.put("comparison", c);
        });
        return out;
    }

    private LoanApplication requireApp(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token is blank — allowing canonical shadow inspect without token (local only)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
