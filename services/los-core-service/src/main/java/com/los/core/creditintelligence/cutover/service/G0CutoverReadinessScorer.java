package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.CiCutoverReadinessSnapshot;
import com.los.core.creditintelligence.cutover.domain.G0ReadinessOutcome;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import com.los.core.creditintelligence.validation.domain.CutoverOutcome;
import com.los.core.creditintelligence.validation.service.CutoverReadinessAssessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G0 readiness scorer (§28). Wraps {@link CutoverReadinessAssessor}.
 * Honest default: overall NOT_READY while silent defaults remain outside quarantine.
 * LIMITED_PILOT_READY only when cohort gates are met.
 */
@Service
public class G0CutoverReadinessScorer {

    public record GateInput(
            boolean criticalBindingsCertified,
            boolean defaultsEliminatedOrQuarantined,
            boolean realDataValidationOk,
            boolean dualRunReviewed,
            boolean policyCertified,
            boolean decisionCertified,
            boolean replayDeterministic,
            boolean tenantIsolationOk,
            boolean securityOk,
            boolean performanceOk,
            boolean rollbackProven,
            boolean observabilityOk,
            boolean fixtureOnlyEvidence,
            int silentDefaultsOutsideQuarantine,
            boolean narrowCohortDefined
    ) {
    }

    public record ScoreResult(
            G0ReadinessOutcome overall,
            G0ReadinessOutcome cohortOutcome,
            BigDecimal score,
            Map<String, Object> dimensions,
            List<String> blockers,
            boolean limitedPilotReady,
            CutoverReadinessAssessor.Assessment legacyAssessment
    ) {
    }

    private final CutoverStore store;
    private final CutoverReadinessAssessor legacyAssessor;

    public G0CutoverReadinessScorer(CutoverStore store) {
        this(store, new CutoverReadinessAssessor());
    }

    @Autowired
    public G0CutoverReadinessScorer(CutoverStore store, CutoverReadinessAssessor legacyAssessor) {
        this.store = store;
        this.legacyAssessor = legacyAssessor != null ? legacyAssessor : new CutoverReadinessAssessor();
    }

    public ScoreResult score(UUID tenantId, UUID cohortId, GateInput gates) {
        List<String> blockers = new ArrayList<>();
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("criticalBindingCertification", gates.criticalBindingsCertified());
        dimensions.put("defaultElimination", gates.defaultsEliminatedOrQuarantined());
        dimensions.put("realDataValidation", gates.realDataValidationOk());
        dimensions.put("dualRunMatch", gates.dualRunReviewed());
        dimensions.put("policyCertification", gates.policyCertified());
        dimensions.put("decisionCertification", gates.decisionCertified());
        dimensions.put("replay", gates.replayDeterministic());
        dimensions.put("tenantIsolation", gates.tenantIsolationOk());
        dimensions.put("security", gates.securityOk());
        dimensions.put("performance", gates.performanceOk());
        dimensions.put("rollbackReadiness", gates.rollbackProven());
        dimensions.put("operationalObservability", gates.observabilityOk());
        dimensions.put("fixtureOnlyEvidence", gates.fixtureOnlyEvidence());
        dimensions.put("silentDefaultsOutsideQuarantine", gates.silentDefaultsOutsideQuarantine());
        dimensions.put("narrowCohortDefined", gates.narrowCohortDefined());

        if (!gates.criticalBindingsCertified()) blockers.add("Critical bindings not certified");
        if (!gates.defaultsEliminatedOrQuarantined()) {
            blockers.add("Silent unsafe defaults remain outside quarantine");
        }
        if (gates.silentDefaultsOutsideQuarantine() > 0) {
            blockers.add("Silent default deps outside quarantine = "
                    + gates.silentDefaultsOutsideQuarantine());
        }
        if (!gates.realDataValidationOk()) {
            blockers.add(gates.fixtureOnlyEvidence()
                    ? "Validation evidence is fixture-only (penalized)"
                    : "Real/stored multi-source validation incomplete");
        }
        if (!gates.dualRunReviewed()) blockers.add("Dual-run not reviewed");
        if (!gates.policyCertified()) blockers.add("Policy package not certified");
        if (!gates.decisionCertified()) blockers.add("Decision strategy not certified (or hybrid not acknowledged)");
        if (!gates.replayDeterministic()) blockers.add("Replay not deterministic");
        if (!gates.tenantIsolationOk()) blockers.add("Tenant isolation failed");
        if (!gates.securityOk()) blockers.add("Security gate failed");
        if (!gates.performanceOk()) blockers.add("Performance gate failed");
        if (!gates.rollbackProven()) blockers.add("Rollback not proven");
        if (!gates.observabilityOk()) blockers.add("Observability incomplete");

        // Legacy assessor honesty alignment
        CutoverReadinessAssessor.Assessment legacy = legacyAssessor.assess(
                tenantId,
                gates.replayDeterministic(),
                gates.criticalBindingsCertified() ? BigDecimal.valueOf(100) : BigDecimal.valueOf(50),
                gates.criticalBindingsCertified() ? BigDecimal.valueOf(100) : BigDecimal.valueOf(40),
                gates.silentDefaultsOutsideQuarantine(),
                gates.tenantIsolationOk(),
                true,
                gates.realDataValidationOk() || gates.fixtureOnlyEvidence(),
                gates.securityOk() ? 0 : 1,
                gates.performanceOk());
        dimensions.put("legacyAssessorOutcome", legacy.outcome().name());

        int passed = 0;
        int total = 12;
        if (gates.criticalBindingsCertified()) passed++;
        if (gates.defaultsEliminatedOrQuarantined()) passed++;
        if (gates.realDataValidationOk()) passed++;
        if (gates.dualRunReviewed()) passed++;
        if (gates.policyCertified()) passed++;
        if (gates.decisionCertified()) passed++;
        if (gates.replayDeterministic()) passed++;
        if (gates.tenantIsolationOk()) passed++;
        if (gates.securityOk()) passed++;
        if (gates.performanceOk()) passed++;
        if (gates.rollbackProven()) passed++;
        if (gates.observabilityOk()) passed++;
        BigDecimal score = BigDecimal.valueOf(passed * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP);

        // Overall platform readiness — typically NOT_READY in G0
        G0ReadinessOutcome overall;
        if (blockers.isEmpty() && !gates.fixtureOnlyEvidence()
                && gates.silentDefaultsOutsideQuarantine() == 0) {
            overall = G0ReadinessOutcome.READY;
        } else {
            overall = G0ReadinessOutcome.NOT_READY;
        }
        // Silent defaults anywhere outside quarantine force NOT_READY overall
        if (gates.silentDefaultsOutsideQuarantine() > 0
                || legacy.outcome() == CutoverOutcome.NOT_READY) {
            overall = G0ReadinessOutcome.NOT_READY;
        }

        boolean limitedPilot = gates.narrowCohortDefined()
                && gates.criticalBindingsCertified()
                && gates.defaultsEliminatedOrQuarantined()
                && gates.silentDefaultsOutsideQuarantine() == 0
                && gates.replayDeterministic()
                && gates.policyCertified()
                && gates.dualRunReviewed()
                && gates.rollbackProven()
                && gates.securityOk()
                && gates.decisionCertified();

        G0ReadinessOutcome cohortOutcome = limitedPilot
                ? G0ReadinessOutcome.LIMITED_PILOT_READY
                : G0ReadinessOutcome.NOT_READY;

        // Fixture-only: may still be LIMITED_PILOT_READY for a cohort if other gates met,
        // but overall stays NOT_READY and dimensions note the penalty.
        if (limitedPilot && gates.fixtureOnlyEvidence()) {
            dimensions.put("limitedPilotCaveat",
                    "LIMITED_PILOT_READY on fixture evidence only — not full READY");
        }

        CiCutoverReadinessSnapshot snap = CiCutoverReadinessSnapshot.builder()
                .cohortId(cohortId)
                .overallOutcome(overall.name())
                .score(score)
                .dimensions(dimensions)
                .blockers(blockers)
                .limitedPilotReady(limitedPilot)
                .createdAt(Instant.now())
                .build();
        // Stash cohort outcome in dimensions for API
        dimensions.put("cohortOutcome", cohortOutcome.name());
        store.saveReadiness(snap);

        return new ScoreResult(overall, cohortOutcome, score, dimensions, blockers, limitedPilot, legacy);
    }
}
