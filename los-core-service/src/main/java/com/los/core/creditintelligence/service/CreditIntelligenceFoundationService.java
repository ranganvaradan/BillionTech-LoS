package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.banking.service.BankingIngestionService;
import com.los.core.creditintelligence.gst.service.GstIngestionService;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.ShadowPolicyRoutingService;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationFreezeService;
import com.los.core.creditintelligence.tax.service.TaxIngestionService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalShadowUnderwritingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrator invoked from LoanApplicationFlowService.
 * Failures are swallowed so production underwriting is never blocked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditIntelligenceFoundationService {

    private final CreditIntelligenceProperties properties;
    private final UnderwritingFactSnapshotBuilder snapshotBuilder;
    private final PolicyVersionResolver policyVersionResolver;
    private final ShadowCreditEvaluationService shadowCreditEvaluationService;
    private final ExecutorService creditIntelligenceShadowExecutor;
    private final GstIngestionService gstIngestionService;
    private final BankingIngestionService bankingIngestionService;
    private final TaxIngestionService taxIngestionService;
    private final ShadowPolicyRoutingService shadowPolicyRoutingService;
    private final CanonicalApplicationConfigurationFreezeService canonicalApplicationConfigurationFreezeService;
    private final CanonicalShadowUnderwritingService canonicalShadowUnderwritingService;

    public record PrepResult(CiFactSnapshot snapshot, CiPolicyVersion policyVersion, UUID tenantId) {
    }

    public boolean isEnabledFor(LoanApplication app) {
        return properties.getFoundation() != null && properties.getFoundation().isEnabled();
    }

    public boolean isShadowEnabledFor(LoanApplication app) {
        if (properties.getShadowEvaluation() == null || !properties.getShadowEvaluation().isEnabled()) {
            return false;
        }
        UUID tenantId = properties.getDefaultTenantId();
        List<String> tenantIds = properties.getShadowEvaluation().getTenantIds();
        if (tenantIds != null && !tenantIds.isEmpty()) {
            String tid = tenantId.toString();
            boolean tenantOk = tenantIds.stream().anyMatch(t -> tid.equalsIgnoreCase(t));
            if (!tenantOk) {
                return false;
            }
        }
        List<String> products = properties.getShadowEvaluation().getProductCodes();
        if (products != null && !products.isEmpty()) {
            String product = app.getLoanProduct() != null ? app.getLoanProduct() : "";
            boolean productOk = products.stream()
                    .anyMatch(p -> product.equalsIgnoreCase(p));
            if (!productOk) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build/freeze snapshot + policy. Returns empty on any failure or when foundation disabled.
     */
    public Optional<PrepResult> prepare(
            LoanApplication app,
            EffectiveUnderwritingContext ctx,
            String kycOutcome,
            String createdBy) {
        if (!isEnabledFor(app)) {
            return Optional.empty();
        }
        try {
            try {
                gstIngestionService.ensureIngested(app.getId());
            } catch (Exception e) {
                log.warn("GST ensureIngested skipped for {}: {}", app.getId(), e.getMessage());
            }
            try {
                bankingIngestionService.ensureIngested(app.getId());
            } catch (Exception e) {
                log.warn("Banking ensureIngested skipped for {}: {}", app.getId(), e.getMessage());
            }
            try {
                taxIngestionService.ensureIngested(app.getId());
            } catch (Exception e) {
                log.warn("Tax ensureIngested skipped for {}: {}", app.getId(), e.getMessage());
            }
            UnderwritingFactSnapshotBuilder.FoundationPrep prep =
                    snapshotBuilder.buildAndFreeze(app, ctx, kycOutcome, createdBy);
            CiPolicyVersion policyVersion = policyVersionResolver.resolveAndFreeze(app, createdBy);
            try {
                // W11.2 observe-only: freeze canonical identity package for later shadow.
                // Failure must not change live underwriting authority or outcome.
                canonicalApplicationConfigurationFreezeService.freezeObservably(app);
            } catch (Exception freezeEx) {
                log.warn("Canonical application configuration freeze ignored for {}: {}",
                        app.getId(), freezeEx.getMessage());
            }
            return Optional.of(new PrepResult(
                    prep.snapshot(),
                    policyVersion,
                    properties.getDefaultTenantId()));
        } catch (Exception ex) {
            log.warn("Credit intelligence prepare failed for {}: {}", app.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * After production underwriting evaluation is recorded, optionally run shadow.
     * Never throws to caller. Never alters production outcome / CAM / sanction.
     * Canonical W11.3 shadow is independent of the legacy ShadowCreditEvaluationService flag.
     */
    public void afterProduction(
            PrepResult prep,
            LoanApplication app,
            UnderwritingEvaluation productionEval,
            MultiRuleEvalResult multi,
            String productionOutcome) {
        if (app == null) {
            return;
        }
        dispatchCanonicalShadow(app, productionEval, multi, productionOutcome);
        if (prep == null) {
            return;
        }
        if (!isShadowEnabledFor(app)) {
            return;
        }
        try {
            UUID snapshotId = prep.snapshot().getId();
            UUID applicationId = app.getId();
            final String outcome = productionOutcome != null
                    ? productionOutcome
                    : (multi != null ? multi.aggregateCreditDecision() : null);
            Runnable task = () -> {
                try {
                    Optional<ShadowPolicyRoutingService.RoutingResult> routed =
                            shadowPolicyRoutingService.routeShadow(app, null, null);
                    if (routed.isEmpty()) {
                        log.info("shadow_skip_eval app={} — routing unavailable", applicationId);
                        return;
                    }
                    ShadowPolicyRoutingService.RoutingResult r = routed.get();
                    if (!r.executableForShadow()
                            || !PolicyApplicabilityResolver.EXACTLY_ONE.equals(r.outcome())
                            || r.policyVersionId() == null) {
                        log.info("shadow_skip_eval app={} catalogueOutcome={} executable={} — no silent legacy fallback",
                                applicationId, r.outcome(), r.executableForShadow());
                        return;
                    }
                    // Exact immutable package from durable catalogue — never latest/live lookup
                    shadowCreditEvaluationService.evaluate(
                            snapshotId, r.policyVersionId(), applicationId, productionEval, outcome);
                } catch (Exception ex) {
                    log.warn("Shadow evaluation task failed for {}: {}", applicationId, ex.getMessage());
                }
            };
            if (properties.getShadowEvaluation().isAsync()) {
                creditIntelligenceShadowExecutor.submit(task);
            } else {
                task.run();
            }
        } catch (Exception ex) {
            log.warn("afterProduction shadow dispatch failed for {}: {}", app.getId(), ex.getMessage());
        }
    }

    private void dispatchCanonicalShadow(
            LoanApplication app,
            UnderwritingEvaluation productionEval,
            MultiRuleEvalResult multi,
            String productionOutcome) {
        try {
            Runnable task = () -> canonicalShadowUnderwritingService.afterLiveDecision(
                    app, productionEval, multi, productionOutcome);
            CreditIntelligenceProperties.CanonicalShadow cfg = properties.getCanonicalShadow();
            if (cfg != null && cfg.isAsync()) {
                creditIntelligenceShadowExecutor.submit(task);
            } else {
                task.run();
            }
        } catch (Exception ex) {
            log.warn("canonical shadow dispatch failed for {}: {}", app.getId(), ex.getMessage());
        }
    }
}
