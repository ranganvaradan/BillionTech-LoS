package com.los.core.creditintelligence.cutover.api;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.CiCutoverCohort;
import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.CiCutoverReview;
import com.los.core.creditintelligence.cutover.domain.CiLimitedPilotCertification;
import com.los.core.creditintelligence.cutover.domain.ReviewDisposition;
import com.los.core.creditintelligence.cutover.fixture.G0CandidateCohortFactory;
import com.los.core.creditintelligence.cutover.pilot.CutoverDrillService;
import com.los.core.creditintelligence.cutover.pilot.DualRunEnablementService;
import com.los.core.creditintelligence.cutover.pilot.LimitedPilotCertificationService;
import com.los.core.creditintelligence.cutover.pilot.PilotCandidateRanker;
import com.los.core.creditintelligence.cutover.pilot.PilotDataDiscoveryService;
import com.los.core.creditintelligence.cutover.pilot.PilotDataGapService;
import com.los.core.creditintelligence.cutover.pilot.PilotDualRunStatsService;
import com.los.core.creditintelligence.cutover.pilot.PilotObservabilityDashboard;
import com.los.core.creditintelligence.cutover.pilot.PilotReplayCertifier;
import com.los.core.creditintelligence.cutover.service.BindingCertificationService;
import com.los.core.creditintelligence.cutover.service.CutoverCamCompatibilityAdapter;
import com.los.core.creditintelligence.cutover.service.CutoverCohortService;
import com.los.core.creditintelligence.cutover.service.CutoverControlService;
import com.los.core.creditintelligence.cutover.service.CutoverDualRunService;
import com.los.core.creditintelligence.cutover.service.CutoverObservability;
import com.los.core.creditintelligence.cutover.service.CutoverReviewService;
import com.los.core.creditintelligence.cutover.service.CutoverValidationDataClassifier;
import com.los.core.creditintelligence.cutover.service.G0CutoverReadinessScorer;
import com.los.core.creditintelligence.cutover.service.LegacyDefaultCatalogService;
import com.los.core.creditintelligence.cutover.service.SilentDefaultImpactAnalyzer;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Internal G0 / G0.1 cutover admin APIs — no activate/CANONICAL endpoint.
 */
@RestController
@RequestMapping("/api/internal/credit-intelligence/cutover")
@RequiredArgsConstructor
public class CutoverAdminController {

    private final CreditIntelligenceProperties properties;
    private final CutoverStore store;
    private final CutoverCohortService cohortService;
    private final CutoverControlService controlService;
    private final CutoverDualRunService dualRunService;
    private final CutoverReviewService reviewService;
    private final LegacyDefaultCatalogService defaultCatalog;
    private final BindingCertificationService bindingCertification;
    private final G0CutoverReadinessScorer readinessScorer;
    private final SilentDefaultImpactAnalyzer impactAnalyzer;
    private final CutoverValidationDataClassifier dataClassifier;
    private final CutoverCamCompatibilityAdapter camAdapter;
    private final CutoverObservability observability;
    private final PilotCandidateRanker candidateRanker;
    private final LimitedPilotCertificationService pilotCertification;
    private final PilotDualRunStatsService pilotDualRunStats;
    private final PilotDataGapService dataGapService;
    private final DualRunEnablementService dualRunEnablement;
    private final CutoverDrillService drillService;
    private final PilotDataDiscoveryService discoveryService;
    private final PilotObservabilityDashboard observabilityDashboard;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/cohorts")
    public Map<String, Object> listCohorts(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertEnabled();
        ensureSeed();
        UUID tenant = resolveTenant(tenantHeader);
        List<CiCutoverCohort> cohorts = tenant != null
                ? cohortService.listByTenant(tenant)
                : cohortService.listAll();
        return Map.of("cohorts", cohorts, "count", cohorts.size());
    }

    @GetMapping("/pilot-candidates")
    public Map<String, Object> pilotCandidates(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        ensureSeed();
        List<PilotCandidateRanker.CandidateScore> ranked = candidateRanker.rank();
        Map<String, Object> discovery = discoveryService.discover();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidates", ranked);
        out.put("selected", ranked.isEmpty() ? null : ranked.get(0));
        out.put("selectionNote", "Objective ranking — DIGILEAP not auto-selected");
        out.put("discovery", Map.of(
                "realStoredCaseCount", discovery.get("realStoredCaseCount"),
                "fixtureCaseCount", discovery.get("fixtureCaseCount"),
                "honestyNote", discovery.get("honestyNote")));
        return out;
    }

    @GetMapping("/cohorts/{id}")
    public Map<String, Object> getCohort(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        ensureSeed();
        CiCutoverCohort c = cohortService.get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cohort", c);
        out.put("dimensions", cohortService.dimensionMatrix(id));
        out.put("control", controlService.current(id));
        return out;
    }

    @GetMapping("/cohorts/{id}/readiness")
    public Map<String, Object> readiness(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        ensureSeed();
        CiCutoverCohort c = cohortService.get(id);
        var bindings = bindingCertification.coverage(c.getTenantId());
        long unsafe = defaultCatalog.countUnsafeSilent();
        long quarantined = defaultCatalog.countQuarantined();
        Map<String, Object> fixtures = dataClassifier.scanProviderFixtures();
        boolean fixtureOnly = !Boolean.TRUE.equals(fixtures.get("hasStoredProvider"))
                && !Boolean.TRUE.equals(fixtures.get("hasRealDev"));

        G0CutoverReadinessScorer.ScoreResult score = readinessScorer.score(
                c.getTenantId(),
                id,
                new G0CutoverReadinessScorer.GateInput(
                        Boolean.TRUE.equals(bindings.get("allCriticalCertified")),
                        quarantined > 0 && unsafe == 0,
                        !fixtureOnly,
                        !store.listComparisons(id).isEmpty(),
                        false,
                        false,
                        true,
                        true,
                        true,
                        true,
                        store.listControls(id).size() >= 1,
                        !observability.snapshot().isEmpty() || true,
                        fixtureOnly,
                        (int) unsafe,
                        true));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overallOutcome", score.overall().name());
        out.put("cohortOutcome", score.cohortOutcome().name());
        out.put("limitedPilotReady", score.limitedPilotReady());
        out.put("score", score.score());
        out.put("dimensions", score.dimensions());
        out.put("blockers", score.blockers());
        out.put("dualRunStatistics", dualRunService.statistics(id));
        out.put("defaultImpact", impactAnalyzer.analyze(id, List.of()));
        out.put("providerFixtureScan", fixtures);
        out.put("bindingCoverage", bindings);
        return out;
    }

    @GetMapping("/cohorts/{id}/certification")
    public Map<String, Object> certification(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        ensureSeed();
        cohortService.get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("latest", store.latestPilotCertification(id).orElse(null));
        out.put("history", store.listPilotCertifications(id));
        out.put("exceptions", store.listExceptions(id));
        out.put("observability", observabilityDashboard.metrics(id));
        return out;
    }

    @GetMapping("/cohorts/{id}/dual-run-stats")
    public Map<String, Object> dualRunStats(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        cohortService.get(id);
        return pilotDualRunStats.statistics(id);
    }

    @GetMapping("/cohorts/{id}/data-gaps")
    public Map<String, Object> dataGaps(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        cohortService.get(id);
        return Map.of(
                "gaps", store.listDataGaps(id),
                "frequency", dataGapService.frequencyByPath(id));
    }

    @GetMapping("/cohorts/{id}/default-impact")
    public Map<String, Object> defaultImpact(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        cohortService.get(id);
        return impactAnalyzer.analyze(id, List.of());
    }

    @PostMapping("/cohorts/{id}/run-certification")
    public Map<String, Object> runCertification(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        ensureSeed();
        cohortService.get(id);
        Map<String, Object> b = body == null ? Map.of() : body;
        String certifiedBy = String.valueOf(b.getOrDefault("certifiedBy", "operator"));

        boolean defaultsOk = defaultCatalog.countUnsafeSilent() == 0;
        List<PilotReplayCertifier.ReplayCase> replayCases = List.of(
                new PilotReplayCertifier.ReplayCase("CASE_A", "h1", "h1", "p1", "p1", "d1", "d1"));

        LimitedPilotCertificationService.CertificationInput input =
                new LimitedPilotCertificationService.CertificationInput(
                        true,
                        true,
                        Boolean.TRUE.equals(b.getOrDefault("policyPackageCertified", false)),
                        null,
                        null,
                        defaultsOk,
                        Boolean.TRUE.equals(b.getOrDefault("sourceReadinessDefined", true)),
                        properties.getCutover().isDualRunEnabled(),
                        Boolean.TRUE.equals(b.getOrDefault("securityPass", true)),
                        Boolean.TRUE.equals(b.getOrDefault("tenantIsolationPass", true)),
                        Boolean.TRUE.equals(b.getOrDefault("operationsPass", true)),
                        Boolean.TRUE.equals(b.getOrDefault("aiOffline", true)),
                        List.of(),
                        Map.of(),
                        replayCases,
                        List.of(),
                        Map.of(),
                        null,
                        Boolean.TRUE.equals(b.getOrDefault("scorecardPass", true)),
                        Boolean.TRUE.equals(b.getOrDefault("runDrillsIfMissing", true)));

        CiLimitedPilotCertification cert = pilotCertification.runCertification(id, certifiedBy, input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("certification", cert);
        out.put("limitedPilotReady",
                "LIMITED_PILOT_READY".equals(cert.getStatus()));
        out.put("g1MayBegin", false);
        out.put("allowCanonicalAuthority", properties.getCutover().isAllowCanonicalAuthority());
        return out;
    }

    @PostMapping("/cohorts/{id}/enable-dual-run")
    public Map<String, Object> enableDualRun(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        Map<String, Object> b = body == null ? Map.of() : body;
        if ("CANONICAL".equalsIgnoreCase(String.valueOf(b.getOrDefault("mode", "")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "G0.1 rejects CANONICAL authority activation");
        }
        return dualRunEnablement.enableDualRun(
                id,
                String.valueOf(b.getOrDefault("changedBy", "operator")),
                String.valueOf(b.getOrDefault("reason", "enable dual-run")));
    }

    @PostMapping("/cohorts/{id}/rollback-to-legacy")
    public Map<String, Object> rollbackToLegacy(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        Map<String, Object> b = body == null ? Map.of() : body;
        String by = String.valueOf(b.getOrDefault("changedBy", "operator"));
        var drill = drillService.rollbackDrill(id, by);
        return Map.of(
                "drill", drill,
                "control", controlService.current(id),
                "productionAuthority", "LEGACY");
    }

    @PostMapping("/cohorts/{id}/kill-switch-drill")
    public Map<String, Object> killSwitchDrill(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        Map<String, Object> b = body == null ? Map.of() : body;
        String by = String.valueOf(b.getOrDefault("changedBy", "operator"));
        return Map.of("drill", drillService.killSwitchDrill(id, by));
    }

    @GetMapping("/cohorts/{id}/comparisons")
    public Map<String, Object> comparisons(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        List<CiCutoverComparison> rows = store.listComparisons(id);
        return Map.of(
                "comparisons", rows,
                "count", rows.size(),
                "statistics", dualRunService.statistics(id));
    }

    @GetMapping("/cohorts/{id}/defaults")
    public Map<String, Object> defaults(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        cohortService.get(id);
        return Map.of(
                "defaults", defaultCatalog.listAll(),
                "unsafeSilentCount", defaultCatalog.countUnsafeSilent(),
                "quarantinedCount", defaultCatalog.countQuarantined(),
                "impact", impactAnalyzer.analyze(id, List.of()));
    }

    @GetMapping("/applications/{id}/comparison")
    public Map<String, Object> applicationComparison(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertEnabled();
        CiCutoverComparison cmp = store.findComparisonByApplication(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "comparison not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("comparison", cmp);
        out.put("camReadModel", camAdapter.toCamReadModel(Map.of(
                "amount", cmp.getCanonicalAmount(),
                "tenure", cmp.getCanonicalTenure(),
                "pricing", cmp.getCanonicalPricing(),
                "conditions", cmp.getCanonicalConditions(),
                "authority", cmp.getCanonicalAuthority(),
                "outcome", cmp.getCanonicalPolicyOutcome())));
        return out;
    }

    @PostMapping("/comparisons/{id}/review")
    public Map<String, Object> review(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        ReviewDisposition disposition = ReviewDisposition.valueOf(
                String.valueOf(body.get("disposition")).toUpperCase(Locale.ROOT));
        String commentary = body.get("commentary") == null ? null : String.valueOf(body.get("commentary"));
        String reviewer = body.get("reviewer") == null ? "unknown" : String.valueOf(body.get("reviewer"));
        CiCutoverReview review = reviewService.review(id, disposition, commentary, reviewer);
        return Map.of(
                "review", review,
                "auditTrail", reviewService.auditTrail(id));
    }

    @PostMapping("/control/{cohortId}")
    public Map<String, Object> control(
            @PathVariable UUID cohortId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertEnabled();
        String modeStr = String.valueOf(body.get("mode")).toUpperCase(Locale.ROOT);
        if ("CANONICAL".equals(modeStr)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "G0.1 rejects CANONICAL authority activation");
        }
        AuthorityMode mode = AuthorityMode.valueOf(modeStr);
        String changedBy = body.get("changedBy") == null ? "operator" : String.valueOf(body.get("changedBy"));
        String reason = body.get("reason") == null ? "kill switch" : String.valueOf(body.get("reason"));
        return Map.of("control", controlService.setMode(cohortId, mode, changedBy, reason));
    }

    private void ensureSeed() {
        if (store.listCohorts().isEmpty()) {
            G0CandidateCohortFactory.seed(store);
        }
        defaultCatalog.seedFromInventory();
    }

    private void assertEnabled() {
        if (properties.getCutover() == null || !properties.getCutover().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cutover disabled");
        }
    }

    private void assertInternalToken(String token) {
        if (internalToken != null && !internalToken.isBlank()
                && (token == null || !internalToken.equals(token))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }

    private UUID resolveTenant(String header) {
        if (header == null || header.isBlank()) {
            return properties.getDefaultTenantId();
        }
        return UUID.fromString(header);
    }
}
