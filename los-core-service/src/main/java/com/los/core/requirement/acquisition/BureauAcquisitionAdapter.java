package com.los.core.requirement.acquisition;

import com.los.core.requirement.AcquisitionDtos;
import com.los.core.requirement.AcquisitionExecutorPort;
import com.los.core.requirement.AcquisitionSourceResolver;
import com.los.core.requirement.SourceAcquisitionState;
import com.los.core.requirement.W6EvaluationContextFactory;
import com.los.core.service.flow.step.BureauPullStepExecutor;
import com.los.core.service.flow.step.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thin adapter — reuses existing {@link BureauPullStepExecutor} (certified bureau path).
 * No simulation; unavailable credentials remain unavailable. Does not invent scores.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BureauAcquisitionAdapter implements AcquisitionExecutorPort {

    private final BureauPullStepExecutor bureauPullStepExecutor;

    @Override
    public String sourceKey() {
        return AcquisitionSourceResolver.BUREAU;
    }

    @Override
    public boolean supports(String sourceKey) {
        return AcquisitionSourceResolver.BUREAU.equals(AcquisitionSourceResolver.normalize(sourceKey));
    }

    @Override
    public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
        if (ctx.dryRun()) {
            return AcquisitionDtos.ExecutorOutcome.succeeded("BUREAU_DRY_RUN", Map.of(), Map.of("dryRun", true));
        }
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("orchestratedBy", "W6_WorkflowAcquisitionCoordinator");
            context.put("requirementItemId", ctx.item().getId() != null ? ctx.item().getId().toString() : null);
            context.put("canonicalParameterId", ctx.item().getCanonicalParameterId());
            StepResult result = bureauPullStepExecutor.execute(ctx.applicationId(), context);
            Map<String, Object> summary = new LinkedHashMap<>();
            if (result != null && result.output() != null) {
                summary.putAll(result.output());
            }
            boolean success = result != null && result.success();
            if (!success) {
                String reason = result != null && result.nextActionHint() != null
                        ? result.nextActionHint()
                        : String.valueOf(summary.getOrDefault("error", "Bureau pull failed"));
                if (isRetryable(reason)) {
                    return AcquisitionDtos.ExecutorOutcome.retryable("BUREAU", reason, summary);
                }
                return AcquisitionDtos.ExecutorOutcome.terminal("BUREAU", reason, summary);
            }
            Object score = summary.get("creditScore");
            boolean scorePresent = isUsableScore(score) && !Boolean.FALSE.equals(summary.get("scorePresent"));
            if (summary.containsKey("scorePresent")) {
                scorePresent = Boolean.TRUE.equals(summary.get("scorePresent"));
            }
            // Source success ≠ parameter available. Only claim legacy fixture readiness;
            // GACAT IDs are decided by CanonicalParameterExecutionService after reconcile.
            String param = ctx.item().getCanonicalParameterId();
            Map<String, Boolean> facts = new LinkedHashMap<>(
                    W6EvaluationContextFactory.acquisitionClaim(param, scorePresent));
            Optional<String> gacat = W6EvaluationContextFactory.resolveGacatId(param);
            if (gacat.isPresent() && !"bureau.score".equals(gacat.get()) && scorePresent) {
                summary.put("bureauScoreAvailableButNotTargetParameter", true);
                summary.put("targetCanonicalParameterId", gacat.get());
            }
            summary.put("providerHttpSuccess", true);
            summary.put("scorePresent", scorePresent);
            if (score != null) {
                summary.put("creditScore", score);
            }
            return new AcquisitionDtos.ExecutorOutcome(
                    SourceAcquisitionState.SUCCEEDED,
                    "BUREAU",
                    summary.get("transactionId") != null ? String.valueOf(summary.get("transactionId")) : null,
                    null,
                    null,
                    summary,
                    facts,
                    true);
        } catch (com.los.core.exception.BusinessRuleException e) {
            log.warn("Bureau acquisition unavailable/blocked for app {}: {}", ctx.applicationId(), e.getMessage());
            return AcquisitionDtos.ExecutorOutcome.unavailable("BUREAU", e.getMessage());
        } catch (Exception e) {
            log.warn("Bureau acquisition error for app {}: {}", ctx.applicationId(), e.toString());
            if (isRetryable(e.getMessage())) {
                return AcquisitionDtos.ExecutorOutcome.retryable("BUREAU", e.getMessage(), Map.of());
            }
            return AcquisitionDtos.ExecutorOutcome.terminal("BUREAU", e.getMessage(), Map.of());
        }
    }

    private static boolean isUsableScore(Object score) {
        if (score == null) {
            return false;
        }
        if (score instanceof Number n) {
            return n.intValue() > 0;
        }
        try {
            return Integer.parseInt(String.valueOf(score)) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isRetryable(String reason) {
        if (reason == null) {
            return true;
        }
        String r = reason.toLowerCase();
        return r.contains("timeout") || r.contains("503") || r.contains("429") || r.contains("connection");
    }
}
