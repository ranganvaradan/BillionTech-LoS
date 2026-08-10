package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyShadowRouting;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyShadowRoutingRepository;
import com.los.core.model.entity.LoanApplication;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shadow-only durable policy routing. Never changes production underwriting outcome.
 * Never executes without an immutable linked package (P2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowPolicyRoutingService {

    private final PolicyCatalogueService catalogueService;
    private final CiPolicyShadowRoutingRepository routingRepository;
    private final CreditIntelligenceProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public record RoutingResult(
            String outcome,
            UUID applicabilityId,
            UUID policyVersionId,
            UUID executablePackageId,
            String contentHash,
            String policyName,
            String policyVersion,
            String reason,
            LocalDate evaluationBusinessDate,
            UUID routingRecordId,
            Map<String, Object> evidence,
            Map<String, Object> resolvePayload,
            boolean executableForShadow
    ) {}

    @Transactional
    public Optional<RoutingResult> routeShadow(LoanApplication app, LocalDate evaluationAsOf, UUID evaluationContextId) {
        long t0 = System.currentTimeMillis();
        try {
            if (properties.getCutover() != null && properties.getCutover().isAllowCanonicalAuthority()) {
                log.warn("shadow_routing_refused allowCanonicalAuthority unexpectedly true");
                return Optional.empty();
            }
            UUID tenantId = properties.getDefaultTenantId();
            ApplicationPolicyQuery query = ApplicationPolicyQueryFactory.fromLoanApplication(app, evaluationAsOf);
            LocalDate asOf = query.evaluationDate();
            if (asOf == null) {
                Map<String, Object> noDate = new LinkedHashMap<>();
                noDate.put("outcome", PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
                noDate.put("reason", "No evaluation/business date available on application");
                CiPolicyShadowRouting saved = persistRouting(tenantId, app, null, noDate, evaluationContextId,
                        System.currentTimeMillis() - t0, "SKIPPED_NO_DATE", null);
                return Optional.of(new RoutingResult(
                        PolicyApplicabilityResolver.NO_APPLICABLE_POLICY,
                        null, null, null, null, null, null,
                        String.valueOf(noDate.get("reason")),
                        null, saved.getId(),
                        ApplicationPolicyQueryFactory.evidence(app, null, noDate),
                        noDate, false));
            }

            Map<String, Object> resolved = catalogueService.resolve(tenantId, query);
            Map<String, Object> evidence = ApplicationPolicyQueryFactory.evidence(app, asOf, resolved);
            String outcome = String.valueOf(resolved.get("outcome"));

            UUID applicabilityId = uuidOrNull(resolved.get("resolvedApplicabilityId"));
            UUID policyVersionId = uuidOrNull(resolved.get("resolvedPolicyVersionId"));
            UUID executablePackageId = uuidOrNull(resolved.get("resolvedExecutablePackageId"));
            String contentHash = resolved.get("contentHash") == null ? null : String.valueOf(resolved.get("contentHash"));
            String policyName = null;
            String version = null;
            if (resolved.get("selectedPolicy") instanceof Map<?, ?> sel) {
                policyName = sel.get("policyName") == null ? null : String.valueOf(sel.get("policyName"));
                version = sel.get("policyVersion") == null ? null : String.valueOf(sel.get("policyVersion"));
            } else if (resolved.get("catalogueMatch") instanceof Map<?, ?> sel) {
                policyName = sel.get("policyName") == null ? null : String.valueOf(sel.get("policyName"));
                version = sel.get("policyVersion") == null ? null : String.valueOf(sel.get("policyVersion"));
            }

            boolean executable = PolicyApplicabilityResolver.EXACTLY_ONE.equals(outcome)
                    && policyVersionId != null
                    && Boolean.TRUE.equals(resolved.get("shadowRoutable"));

            long latency = System.currentTimeMillis() - t0;
            String evalStatus = executable ? "ROUTED" : outcome;
            CiPolicyShadowRouting saved = persistRouting(
                    tenantId, app, asOf, resolved, evaluationContextId, latency, evalStatus, null);

            increment("ci.policy.shadow.routing", "outcome", outcome);
            log.info("shadow_policy_routed app={} product={} asOf={} outcome={} policy={} v={} executable={} latencyMs={} shadowOnly=true",
                    app.getId(), query.productCode(), asOf, outcome, policyName, version, executable, latency);

            return Optional.of(new RoutingResult(
                    outcome,
                    applicabilityId,
                    policyVersionId,
                    executablePackageId,
                    contentHash,
                    policyName,
                    version,
                    resolved.get("reason") == null ? null : String.valueOf(resolved.get("reason")),
                    asOf,
                    saved.getId(),
                    evidence,
                    resolved,
                    executable));
        } catch (Exception ex) {
            log.warn("shadow_policy_routing_failed app={} err={}",
                    app == null ? null : app.getId(), ex.getClass().getSimpleName());
            increment("ci.policy.shadow.routing", "outcome", "ERROR");
            try {
                UUID tenantId = properties.getDefaultTenantId();
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("outcome", "ROUTING_ERROR");
                err.put("reason", ex.getMessage());
                persistRouting(tenantId, app,
                        ApplicationPolicyQueryFactory.resolveEvaluationBusinessDate(app, evaluationAsOf),
                        err, evaluationContextId, System.currentTimeMillis() - t0,
                        "ERROR", ex.getMessage());
            } catch (Exception ignored) {
                // never block production
            }
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> latestForApplication(UUID applicationId) {
        return routingRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .findFirst()
                .map(this::toView)
                .orElse(Map.of(
                        "resolverOutcome", "NONE",
                        "shadowOnly", true,
                        "allowCanonicalAuthority", false,
                        "message", "No shadow policy routing recorded yet."));
    }

    private CiPolicyShadowRouting persistRouting(
            UUID tenantId,
            LoanApplication app,
            LocalDate asOf,
            Map<String, Object> resolved,
            UUID evaluationContextId,
            long latencyMs,
            String shadowStatus,
            String error) {
        UUID applicabilityId = uuidOrNull(resolved.get("resolvedApplicabilityId"));
        UUID policyVersionId = uuidOrNull(resolved.get("resolvedPolicyVersionId"));
        UUID executablePackageId = uuidOrNull(resolved.get("resolvedExecutablePackageId"));
        String policyName = null;
        String version = null;
        if (resolved.get("selectedPolicy") instanceof Map<?, ?> sel) {
            policyName = sel.get("policyName") == null ? null : String.valueOf(sel.get("policyName"));
            version = sel.get("policyVersion") == null ? null : String.valueOf(sel.get("policyVersion"));
        } else if (resolved.get("catalogueMatch") instanceof Map<?, ?> sel) {
            policyName = sel.get("policyName") == null ? null : String.valueOf(sel.get("policyName"));
            version = sel.get("policyVersion") == null ? null : String.valueOf(sel.get("policyVersion"));
        }
        String outcome = String.valueOf(resolved.getOrDefault("outcome", "UNKNOWN"));
        CiPolicyShadowRouting row = CiPolicyShadowRouting.builder()
                .tenantId(tenantId)
                .applicationId(app.getId())
                .evaluationContextId(evaluationContextId)
                .evaluationBusinessDate(asOf == null ? LocalDate.of(1970, 1, 1) : asOf)
                .productCode(app.getLoanProduct())
                .requestedAmount(app.getRequestedAmount())
                .borrowerType(app.getBorrowerType() == null ? null : app.getBorrowerType().name())
                .intakeSegment(app.getIntakeSegment() == null ? null : app.getIntakeSegment().name())
                .resolverOutcome(outcome)
                .selectedApplicabilityId(applicabilityId)
                .selectedPolicyVersionId(policyVersionId)
                .executablePackageId(executablePackageId)
                .contentHash(resolved.get("contentHash") == null ? null : String.valueOf(resolved.get("contentHash")))
                .linkageOutcome(resolved.get("linkageClass") == null ? outcome
                        : String.valueOf(resolved.get("linkageClass")))
                .policyName(policyName)
                .policyVersionLabel(version)
                .applicabilityReason(resolved.get("reason") == null ? null : String.valueOf(resolved.get("reason")))
                .evidence(ApplicationPolicyQueryFactory.evidence(app, asOf, resolved))
                .shadowOnly(true)
                .shadowEvaluationStatus(shadowStatus)
                .shadowEvaluationError(error)
                .routingLatencyMs(latencyMs)
                .build();
        return routingRepository.save(row);
    }

    private Map<String, Object> toView(CiPolicyShadowRouting r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", r.getApplicationId().toString());
        m.put("evaluationContextId", r.getEvaluationContextId() == null ? null : r.getEvaluationContextId().toString());
        m.put("evaluationBusinessDate", r.getEvaluationBusinessDate().toString());
        m.put("productCode", r.getProductCode());
        m.put("resolverOutcome", r.getResolverOutcome());
        m.put("policyName", r.getPolicyName());
        m.put("policyVersion", r.getPolicyVersionLabel());
        m.put("policyVersionId", r.getSelectedPolicyVersionId() == null ? null : r.getSelectedPolicyVersionId().toString());
        m.put("executablePackageId", r.getExecutablePackageId() == null ? null : r.getExecutablePackageId().toString());
        m.put("contentHash", r.getContentHash());
        m.put("linkageOutcome", r.getLinkageOutcome());
        m.put("applicabilityReason", r.getApplicabilityReason());
        m.put("evidence", r.getEvidence());
        m.put("shadowOnly", true);
        m.put("evaluationMode", "Shadow");
        m.put("allowCanonicalAuthority", false);
        m.put("productionAuthority", "DISABLED");
        m.put("shadowEvaluationStatus", r.getShadowEvaluationStatus());
        if (PolicyApplicabilityResolver.EXACTLY_ONE.equals(r.getResolverOutcome())) {
            m.put("banner", "ONE POLICY SELECTED");
        } else if (PolicyApplicabilityResolver.NO_APPLICABLE_POLICY.equals(r.getResolverOutcome())) {
            m.put("banner", "No approved policy is applicable to this application.");
        } else if (PolicyApplicabilityResolver.AMBIGUOUS_POLICY_CONFIGURATION.equals(r.getResolverOutcome())) {
            m.put("banner", "Policy configuration requires attention.");
        } else if (PolicyShadowRoutingOutcomes.POLICY_PACKAGE_NOT_EXECUTABLE.equals(r.getResolverOutcome())
                || PolicyShadowRoutingOutcomes.DEMO_ONLY_NOT_ROUTABLE.equals(r.getResolverOutcome())
                || PolicyShadowRoutingOutcomes.NOT_ELIGIBLE_FOR_SHADOW_ROUTING.equals(r.getResolverOutcome())) {
            m.put("banner", "Policy matched but is not executable for shadow (immutable package required).");
        }
        return m;
    }

    private static UUID uuidOrNull(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private void increment(String name, String tag, String value) {
        MeterRegistry reg = meterRegistry.getIfAvailable();
        if (reg != null) {
            reg.counter(name, tag, value == null ? "unknown" : value).increment();
        }
    }
}
