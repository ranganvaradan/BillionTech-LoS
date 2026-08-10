package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.cutover.domain.CiCutoverCohort;
import com.los.core.creditintelligence.cutover.domain.CiLimitedPilotCertification;
import com.los.core.creditintelligence.cutover.domain.CutoverDrillType;
import com.los.core.creditintelligence.cutover.domain.LimitedPilotCertificationStatus;
import com.los.core.creditintelligence.cutover.service.LegacyDefaultCatalogService;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs all mandatory G0.1 gates (§2/§34); emits CiLimitedPilotCertification.
 * AI never affects score. Fixture-only evidence cannot yield silent LIMITED_PILOT_READY.
 */
@Service
public class LimitedPilotCertificationService {

    public record CertificationInput(
            boolean explicitTenant,
            boolean explicitProduct,
            boolean policyPackageCertified,
            UUID policyCertificationId,
            UUID decisionCertificationId,
            boolean defaultsQuarantined,
            boolean sourceReadinessDefined,
            boolean dualRunEnabledFlag,
            boolean securityPass,
            boolean tenantIsolationPass,
            boolean operationsPass,
            boolean aiOffline, // ignored for scoring
            List<String> unresolvedAmbiguities,
            Map<String, Boolean> ambiguityScope,
            List<PilotReplayCertifier.ReplayCase> replayCases,
            List<String> canonicalConsumedLegacyDefaults,
            Map<String, String> legacyDefaultClassifications,
            Integer realStoredCaseCountOverride,
            boolean scorecardPass,
            boolean runDrillsIfMissing
    ) {
        public static CertificationInput defaultsPassing() {
            return new CertificationInput(
                    true, true, true, null, null, true, true, true, true, true, true,
                    true, List.of(), Map.of(), List.of(), List.of(), Map.of(),
                    null, true, true);
        }
    }

    private final CutoverStore store;
    private final CreditIntelligenceProperties properties;
    private final PilotDataDiscoveryService discovery;
    private final PilotCriticalBindingCertificationService bindingCert;
    private final PilotAmbiguityGate ambiguityGate;
    private final PilotDualRunStatsService dualRunStats;
    private final PilotMismatchThresholds mismatchThresholds;
    private final PilotReplayCertifier replayCertifier;
    private final DefaultLeakageAssertor leakageAssertor;
    private final CutoverDrillService drillService;
    private final DualRunEnablementService enablementService;
    private final ConditionsReadinessResolver conditionsResolver;
    private final LegacyDefaultCatalogService defaultCatalog;

    public LimitedPilotCertificationService(
            CutoverStore store,
            CreditIntelligenceProperties properties,
            PilotDataDiscoveryService discovery,
            PilotCriticalBindingCertificationService bindingCert,
            PilotAmbiguityGate ambiguityGate,
            PilotDualRunStatsService dualRunStats,
            PilotMismatchThresholds mismatchThresholds,
            PilotReplayCertifier replayCertifier,
            DefaultLeakageAssertor leakageAssertor,
            CutoverDrillService drillService,
            DualRunEnablementService enablementService,
            ConditionsReadinessResolver conditionsResolver,
            LegacyDefaultCatalogService defaultCatalog) {
        this.store = store;
        this.properties = properties;
        this.discovery = discovery;
        this.bindingCert = bindingCert;
        this.ambiguityGate = ambiguityGate;
        this.dualRunStats = dualRunStats;
        this.mismatchThresholds = mismatchThresholds;
        this.replayCertifier = replayCertifier;
        this.leakageAssertor = leakageAssertor;
        this.drillService = drillService;
        this.enablementService = enablementService;
        this.conditionsResolver = conditionsResolver;
        this.defaultCatalog = defaultCatalog;
    }

    public CiLimitedPilotCertification runCertification(
            UUID cohortId, String certifiedBy, CertificationInput input) {
        CiCutoverCohort cohort = store.findCohort(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cohort not found"));

        CertificationInput in = input == null ? CertificationInput.defaultsPassing() : input;
        // AI offline must not affect score — intentionally unused
        boolean ignoredAi = in.aiOffline();

        List<String> blockers = new ArrayList<>();
        Map<String, Object> gates = new LinkedHashMap<>();

        gates.put("explicitTenant", in.explicitTenant());
        gates.put("explicitProduct", in.explicitProduct());
        if (!in.explicitTenant()) blockers.add("explicit tenant required");
        if (!in.explicitProduct()) blockers.add("explicit product required");

        // Policy certification
        boolean policyOk = in.policyPackageCertified();
        if (in.policyCertificationId() != null) {
            policyOk = store.listPolicyCerts(cohort.getTenantId()).stream()
                    .anyMatch(c -> in.policyCertificationId().equals(c.getId())
                            && ("CERTIFIED".equals(c.getStatus())
                            || "CERTIFIED_FOR_LIMITED_PILOT".equals(c.getStatus())));
        }
        gates.put("policyPackageCertified", policyOk);
        if (!policyOk) blockers.add("canonical policy package not certified");

        // Critical bindings 100%
        var bindingReport = bindingCert.certify(cohort.getTenantId(), cohortId, cohort.getProductCode());
        gates.put("criticalBindingCoverage", bindingReport.coveragePct());
        gates.put("criticalBindingsCertified", bindingReport.allCertified());
        if (!bindingReport.allCertified()) {
            blockers.addAll(bindingReport.blockers());
        }

        // Unsafe defaults quarantined (caller asserts cohort path; catalog count recorded)
        long unsafe = defaultCatalog == null ? 0 : defaultCatalog.countUnsafeSilent();
        boolean defaultsOk = in.defaultsQuarantined();
        gates.put("defaultsQuarantined", defaultsOk);
        gates.put("unsafeSilentOutsideQuarantine", unsafe);
        if (!defaultsOk) {
            blockers.add("unsafe legacy defaults not quarantined for cohort");
        }
        // Source readiness
        gates.put("sourceReadinessDefined", in.sourceReadinessDefined());
        if (!in.sourceReadinessDefined()) blockers.add("canonical source readiness not defined");

        // Replay 100%
        var replay = replayCertifier.certify(in.replayCases());
        gates.put("replay", replayCertifier.asMap(replay));
        if (!in.replayCases().isEmpty() && !replay.pass()) {
            blockers.add("deterministic replay failed: " + replay.failures());
        } else if (in.replayCases().isEmpty()) {
            // allow empty only when caller marks pass via gate — treat empty as fail for honesty
            blockers.add("replay evidence missing");
            gates.put("replayPass", false);
        } else {
            gates.put("replayPass", true);
        }

        // Dual-run flag
        gates.put("dualRunEnabled", in.dualRunEnabledFlag());
        if (!in.dualRunEnabledFlag()) blockers.add("dual-run not enabled (feature flag)");

        // Mismatch thresholds
        var threshold = mismatchThresholds.evaluate(
                store.listComparisons(cohortId),
                replay.failed());
        gates.put("mismatchThresholds", Map.of(
                "pass", threshold.pass(),
                "unexplainedMaterial", threshold.unexplainedMaterial(),
                "bugMismatches", threshold.bugMismatches(),
                "unexplainedMorePermissive", threshold.unexplainedMorePermissive()));
        if (!threshold.pass()) blockers.addAll(threshold.blockers());

        // Ambiguity
        var amb = ambiguityGate.evaluate(in.unresolvedAmbiguities(), in.ambiguityScope());
        gates.put("ambiguityBlocked", amb.blocked());
        gates.put("blockingAmbiguities", amb.blockingClauses());
        if (amb.blocked()) blockers.add("unresolved Policy Studio ambiguities in pilot scope");

        // Default leakage
        var leak = leakageAssertor.assertNoUnsafeLeak(
                cohortId, in.canonicalConsumedLegacyDefaults(), in.legacyDefaultClassifications());
        gates.put("defaultLeakage", leak.leaked());
        if (leak.leaked()) {
            blockers.add(DefaultLeakageAssertor.FAILURE_CODE + ": " + leak.leakedKeys());
        }

        // Conditions explicit
        var conditions = conditionsResolver.resolve(cohortId, false);
        gates.put("conditions", conditionsResolver.asMap(conditions));
        if (conditions.ambiguous()) blockers.add("CONDITIONS dimension ambiguous");

        // Drills
        if (in.runDrillsIfMissing()) {
            if (!drillService.hasPassingDrill(cohortId, CutoverDrillType.ROLLBACK)) {
                drillService.rollbackDrill(cohortId, certifiedBy == null ? "system" : certifiedBy);
            }
            if (!drillService.hasPassingDrill(cohortId, CutoverDrillType.KILL_SWITCH)) {
                drillService.killSwitchDrill(cohortId, certifiedBy == null ? "system" : certifiedBy);
            }
        }
        boolean rollbackOk = drillService.hasPassingDrill(cohortId, CutoverDrillType.ROLLBACK);
        boolean killOk = drillService.hasPassingDrill(cohortId, CutoverDrillType.KILL_SWITCH);
        gates.put("rollbackDrill", rollbackOk);
        gates.put("killSwitchDrill", killOk);
        if (!rollbackOk) blockers.add("rollback drill not PASS");
        if (!killOk) blockers.add("kill-switch drill not PASS");

        gates.put("securityGate", in.securityPass());
        gates.put("tenantIsolation", in.tenantIsolationPass());
        gates.put("operationsGate", in.operationsPass());
        gates.put("scorecardPass", in.scorecardPass());
        gates.put("aiAffectsScore", false);
        gates.put("aiOfflineIgnored", ignoredAi);
        if (!in.securityPass()) blockers.add("security gate failed");
        if (!in.tenantIsolationPass()) blockers.add("tenant isolation failed");
        if (!in.operationsPass()) blockers.add("operations gate failed");
        if (!in.scorecardPass()) blockers.add("scorecard certification failed");

        // Evidence / real-stored minimum — honesty gate
        Map<String, Object> discoveryReport = discovery.discover();
        int realStored = in.realStoredCaseCountOverride() != null
                ? in.realStoredCaseCountOverride()
                : ((Number) discoveryReport.getOrDefault("realStoredCaseCount", 0)).intValue();
        int minRequired = properties.getCutover() == null
                ? 20 : properties.getCutover().getMinRealOrStoredCases();
        boolean evidenceOk = realStored >= minRequired;
        gates.put("realStoredCaseCount", realStored);
        gates.put("minRealOrStoredCases", minRequired);
        gates.put("evidenceMeetsMinimum", evidenceOk);
        gates.put("validationDatasetSummary", Map.of(
                "realStoredCaseCount", realStored,
                "fixtureCaseCount", discoveryReport.get("fixtureCaseCount"),
                "weightedEvidenceScore", discoveryReport.get("weightedEvidenceScore"),
                "honestyNote", discoveryReport.get("honestyNote")));

        Map<String, Object> dualStats = dualRunStats.statistics(cohortId);
        BigDecimal weighted = discoveryReport.get("weightedEvidenceScore") instanceof BigDecimal bd
                ? bd : BigDecimal.ZERO;

        boolean codeGatesPass = blockers.isEmpty();
        // Sample-size is evaluated separately so we never silently LIMITED_PILOT_READY
        List<String> sampleBlockers = new ArrayList<>();
        if (!evidenceOk) {
            sampleBlockers.add("realStoredCaseCount=" + realStored + " < minRealOrStoredCases=" + minRequired);
        }

        boolean hasSampleException = enablementService.hasApprovedSampleSizeException(cohortId);

        LimitedPilotCertificationStatus status;
        String notes;
        if (!codeGatesPass) {
            status = LimitedPilotCertificationStatus.NOT_READY;
            notes = "Mandatory code gates failed";
            blockers.addAll(sampleBlockers);
        } else if (!evidenceOk) {
            if (hasSampleException) {
                status = LimitedPilotCertificationStatus.READY_WITH_EXCEPTIONS;
                notes = "Code gates passed; sample-size exception approved — NOT LIMITED_PILOT_READY";
                gates.put("sampleSizeException", true);
            } else {
                status = LimitedPilotCertificationStatus.NOT_READY;
                notes = "Code gates passed but real/stored evidence below minimum — not LIMITED_PILOT_READY";
                blockers.addAll(sampleBlockers);
            }
        } else {
            status = LimitedPilotCertificationStatus.LIMITED_PILOT_READY;
            notes = "All mandatory gates including real/stored minimum passed";
        }

        gates.put("limitedPilotReady", status == LimitedPilotCertificationStatus.LIMITED_PILOT_READY);
        gates.put("g1MayBegin", false); // G0.1 never authorizes G1 alone under fixture evidence

        @SuppressWarnings("unchecked")
        Map<String, Object> datasetSummary = (Map<String, Object>) gates.get("validationDatasetSummary");
        boolean limitedReady = status == LimitedPilotCertificationStatus.LIMITED_PILOT_READY;
        CiLimitedPilotCertification cert = CiLimitedPilotCertification.builder()
                .cohortId(cohortId)
                .policyCertificationId(in.policyCertificationId())
                .decisionCertificationId(in.decisionCertificationId())
                .validationDatasetSummary(datasetSummary)
                .realStoredCaseCount(realStored)
                .weightedEvidenceScore(weighted)
                .criticalBindingCoverage(bindingReport.coveragePct())
                .replayPassRate(replay.passRate())
                .canonicalSuccessRate(toBd(dualStats.get("canonicalFailureRate") == null ? null
                        : invert(dualStats.get("canonicalFailureRate"))))
                .canonicalDiRate(toBd(dualStats.get("canonicalDiPct")))
                .materialMismatchRate(toBd(dualStats.get("materialMismatchPct")))
                .unresolvedMismatchCount(threshold.unexplainedMaterial() + threshold.unexplainedMorePermissive()
                        + threshold.bugMismatches())
                .securityGate(in.securityPass())
                .rollbackGate(rollbackOk)
                .operationsGate(in.operationsPass())
                .status(status.name())
                .certifiedBy(limitedReady || status == LimitedPilotCertificationStatus.READY_WITH_EXCEPTIONS
                        ? certifiedBy : null)
                .certifiedAt(limitedReady || status == LimitedPilotCertificationStatus.READY_WITH_EXCEPTIONS
                        ? Instant.now() : null)
                .notes(notes)
                .gateResults(gates)
                .blockers(blockers)
                .createdAt(Instant.now())
                .build();

        return store.savePilotCertification(cert);
    }

    private static BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static BigDecimal invert(Object failureRate) {
        if (!(failureRate instanceof BigDecimal bd)) return null;
        return BigDecimal.valueOf(100).subtract(bd).setScale(2, RoundingMode.HALF_UP);
    }
}
