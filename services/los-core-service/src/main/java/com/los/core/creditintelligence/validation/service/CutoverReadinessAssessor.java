package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.validation.domain.CiPolicyCutoverReadiness;
import com.los.core.creditintelligence.validation.domain.CutoverOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Objective cutover readiness scoring. READY only if all gates met — honest default is NOT_READY /
 * READY_WITH_LIMITATIONS while silent production defaults remain.
 */
@Service
public class CutoverReadinessAssessor {

    public record Assessment(
            CiPolicyCutoverReadiness entity,
            CutoverOutcome outcome,
            List<String> blockers,
            Map<String, Object> dimensions
    ) {
    }

    public Assessment assess(
            UUID tenantId,
            boolean replayPurity,
            BigDecimal criticalCoveragePct,
            BigDecimal bindingCoveragePct,
            int silentDefaultDeps,
            boolean tenantIsolationOk,
            boolean providerFixturesOk,
            boolean multiSourceOk,
            int securityCriticalOpen,
            boolean performanceAcceptable) {
        List<String> blockers = new ArrayList<>();
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("replayPurity", replayPurity);
        dimensions.put("criticalCoveragePct", criticalCoveragePct);
        dimensions.put("bindingCoveragePct", bindingCoveragePct);
        dimensions.put("silentDefaultDeps", silentDefaultDeps);
        dimensions.put("tenantIsolationOk", tenantIsolationOk);
        dimensions.put("providerFixturesOk", providerFixturesOk);
        dimensions.put("multiSourceOk", multiSourceOk);
        dimensions.put("securityCriticalOpen", securityCriticalOpen);
        dimensions.put("performanceAcceptable", performanceAcceptable);

        if (!replayPurity) {
            blockers.add("Replay purity not proven");
        }
        if (criticalCoveragePct == null || criticalCoveragePct.compareTo(BigDecimal.valueOf(100)) < 0) {
            blockers.add("Critical rule canonical coverage < 100%");
        }
        if (silentDefaultDeps > 0) {
            blockers.add("Silent default dependency count = " + silentDefaultDeps
                    + " (production CreditControl gap/demo defaults still active)");
        }
        if (bindingCoveragePct == null || bindingCoveragePct.compareTo(BigDecimal.valueOf(100)) < 0) {
            blockers.add("Policy binding ready coverage < 100% for proposed cutover scope");
        }
        if (!tenantIsolationOk) {
            blockers.add("Tenant isolation validation failed");
        }
        if (!providerFixturesOk) {
            blockers.add("Provider fixture contract stack incomplete");
        }
        if (!multiSourceOk) {
            blockers.add("Multi-source validation incomplete");
        }
        if (securityCriticalOpen > 0) {
            blockers.add("Unresolved critical security findings: " + securityCriticalOpen);
        }
        if (!performanceAcceptable) {
            blockers.add("Performance not yet validated at target banking scale");
        }

        CutoverOutcome outcome;
        if (blockers.isEmpty()) {
            outcome = CutoverOutcome.READY;
        } else if (replayPurity && providerFixturesOk && multiSourceOk && tenantIsolationOk
                && securityCriticalOpen == 0) {
            // Partial progress but silent defaults / coverage remain
            outcome = CutoverOutcome.READY_WITH_LIMITATIONS;
            if (silentDefaultDeps > 0) {
                // Honest: silent defaults in production mean we are NOT ready for authority cutover
                outcome = CutoverOutcome.NOT_READY;
            }
        } else {
            outcome = CutoverOutcome.NOT_READY;
        }

        // Explicit honesty gate from C6 constraints
        if (silentDefaultDeps > 0) {
            outcome = CutoverOutcome.NOT_READY;
            if (!blockers.stream().anyMatch(b -> b.contains("Silent default"))) {
                blockers.add("Silent defaults remain in production underwriting");
            }
        }

        CiPolicyCutoverReadiness entity = CiPolicyCutoverReadiness.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .scope("PLATFORM")
                .outcome(outcome.name())
                .replayPurity(replayPurity)
                .criticalCoveragePct(criticalCoveragePct)
                .bindingCoveragePct(bindingCoveragePct)
                .silentDefaultDeps(silentDefaultDeps)
                .tenantIsolationOk(tenantIsolationOk)
                .providerFixturesOk(providerFixturesOk)
                .multiSourceOk(multiSourceOk)
                .securityCriticalOpen(securityCriticalOpen)
                .blockers(blockers)
                .dimensions(dimensions)
                .assessedAt(Instant.now())
                .metadata(Map.of(
                        "p0AiPolicyStudioDesignMayBegin", true,
                        "p1PolicyEngineModernizationMayBegin", false,
                        "reasonP1Blocked", "Silent production defaults + critical coverage < 100%"))
                .build();
        return new Assessment(entity, outcome, blockers, dimensions);
    }
}
