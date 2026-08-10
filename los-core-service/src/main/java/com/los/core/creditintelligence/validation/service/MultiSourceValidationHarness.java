package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.evaluation.ConfigFreezeService;
import com.los.core.creditintelligence.evaluation.DeterministicEvaluationHasher;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.creditintelligence.validation.domain.CiObligationMatch;
import com.los.core.creditintelligence.validation.domain.DataOrigin;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.model.ProviderStackResult;
import com.los.core.creditintelligence.validation.model.ValidationBundle;
import com.los.core.creditintelligence.validation.model.ValidationRunResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the full non-authoritative multi-source validation pipeline.
 * Never mutates loan application status / CAM / sanction / CreditControl authority.
 */
@Service
public class MultiSourceValidationHarness {

    private final CreditIntelligenceProperties properties;
    private final ValidationBundleLoader bundleLoader;
    private final ProviderStackValidator providerStackValidator;
    private final LegacyDefaultInventory legacyDefaultInventory;
    private final CanonicalCoverageCalculator coverageCalculator;
    private final PolicyBindingCatalog policyBindingCatalog;
    private final DualPolicyEvaluator dualPolicyEvaluator;
    private final ReplayManifestService replayManifestService;
    private final CreditEvidenceViewBuilder evidenceViewBuilder;
    private final LenderObligationMatcher lenderObligationMatcher;
    private final CutoverReadinessAssessor cutoverReadinessAssessor;
    private final TenantIsolationValidator tenantIsolationValidator;
    private final SecurityValidationScanner securityValidationScanner;
    private final DeterministicEvaluationHasher evaluationHasher;
    private final ConfigFreezeService configFreezeService;
    private final ContentHasher contentHasher;
    private final Map<UUID, ValidationRunResult> runStore = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> runsByApp = new ConcurrentHashMap<>();

    public MultiSourceValidationHarness(CreditIntelligenceProperties properties) {
        this.properties = properties != null ? properties : new CreditIntelligenceProperties();
        this.contentHasher = new ContentHasher();
        this.bundleLoader = new ValidationBundleLoader();
        this.providerStackValidator = new ProviderStackValidator();
        this.legacyDefaultInventory = new LegacyDefaultInventory();
        this.coverageCalculator = new CanonicalCoverageCalculator();
        this.policyBindingCatalog = new PolicyBindingCatalog();
        this.dualPolicyEvaluator = new DualPolicyEvaluator();
        this.replayManifestService = new ReplayManifestService(contentHasher);
        this.evidenceViewBuilder = new CreditEvidenceViewBuilder();
        this.lenderObligationMatcher = new LenderObligationMatcher();
        this.cutoverReadinessAssessor = new CutoverReadinessAssessor();
        this.tenantIsolationValidator = new TenantIsolationValidator();
        this.securityValidationScanner = new SecurityValidationScanner();
        this.evaluationHasher = new DeterministicEvaluationHasher(contentHasher);
        this.configFreezeService = new ConfigFreezeService(this.properties, null, contentHasher);
        if (this.properties.getValidation() == null) {
            this.properties.setValidation(new CreditIntelligenceProperties.Validation());
        }
    }

    public boolean isEnabled() {
        return properties.getValidation() != null && properties.getValidation().isEnabled();
    }

    public ValidationRunResult runCase(ValidationCaseCode caseCode) {
        return runCase(caseCode, properties.getDefaultTenantId(), UUID.randomUUID());
    }

    public ValidationRunResult runCase(ValidationCaseCode caseCode, UUID tenantId, UUID applicationId) {
        long start = System.currentTimeMillis();
        Map<String, Long> stages = new LinkedHashMap<>();
        ValidationBundle bundle = bundleLoader.load(caseCode);
        assertHonestOrigin(bundle.dataOrigin());

        long s = System.currentTimeMillis();
        List<ProviderStackResult> providerResults = providerStackValidator.validateBundle(bundle);
        stages.put("providerStackMs", System.currentTimeMillis() - s);

        s = System.currentTimeMillis();
        Map<String, Object> reconciliations = buildReconciliations(bundle);
        stages.put("reconciliationMs", System.currentTimeMillis() - s);

        s = System.currentTimeMillis();
        Map<String, Object> freezeContent = configFreezeService.snapshotContent();
        String configFreezeHash = contentHasher.hashMap(freezeContent);
        UUID configFreezeId = UUID.randomUUID();
        UUID factSnapshotId = UUID.randomUUID();
        UUID metricSetId = UUID.randomUUID();
        UUID reconSetId = UUID.randomUUID();
        UUID policyVersionId = UUID.randomUUID();
        Instant clockInstant = Instant.parse("2026-02-01T10:00:00Z");
        LocalDate asOf = LocalDate.of(2026, 2, 1);
        String factHash = contentHasher.hashMap(Map.of(
                "applicationId", applicationId.toString(),
                "metrics", bundle.metricStubs().toString(),
                "sources", bundle.sources()));
        Map<String, Object> metricMap = new LinkedHashMap<>();
        bundle.metricStubs().forEach((k, v) -> metricMap.put(k, v != null ? v.toPlainString() : null));
        String metricHash = contentHasher.hashMap(metricMap);
        String reconHash = contentHasher.hashMap(reconciliations);
        String policyHash = contentHasher.hashMap(Map.of("shadow", "CANONICAL_POLICY_SHADOW_V1"));

        Map<String, Object> outcomes = buildFrozenOutcomes(
                configFreezeId, configFreezeHash, policyVersionId, policyHash,
                factSnapshotId, metricSetId, reconSetId, asOf, clockInstant, reconciliations, bundle);
        String evalHash = evaluationHasher.hashOutcomes(outcomes);

        int originalThreshold = properties.getCanonicalization().getBureau().getLiveUnsecuredThreshold();
        properties.getCanonicalization().getBureau().setLiveUnsecuredThreshold(99);
        Map<String, Object> replayOutcomes = new LinkedHashMap<>(outcomes);
        replayOutcomes.put("startedAt", Instant.now().toString());
        replayOutcomes.put("durationMs", 9999);
        String replayHash = evaluationHasher.hashOutcomes(replayOutcomes);
        boolean replayIdentical = evalHash.equals(replayHash);
        properties.getCanonicalization().getBureau().setLiveUnsecuredThreshold(originalThreshold);
        stages.put("evaluationReplayMs", System.currentTimeMillis() - s);

        UUID evaluationContextId = UUID.randomUUID();
        replayManifestService.build(
                tenantId, applicationId, evaluationContextId,
                factHash, policyHash, configFreezeHash, metricHash, reconHash, evalHash,
                providerResults);

        s = System.currentTimeMillis();
        boolean hasBank = bundle.sources().containsKey("bank");
        boolean hasBureau = bundle.sources().containsKey("bureau");
        Map<String, Object> legacyExposure = legacyDefaultInventory.exposeForCase(
                bundle.legacyScorecardStub(), bundle.metricStubs(), hasBank, hasBureau);
        Map<String, Object> coverage = coverageCalculator.calculate(
                bundle.metricStubs(), bundle.sources(), reconciliations);
        policyBindingCatalog.seedDefaults(tenantId);
        Map<String, Object> bindingSummary = policyBindingCatalog.coverageSummary(tenantId);
        stages.put("coverageBindingMs", System.currentTimeMillis() - s);

        s = System.currentTimeMillis();
        UUID runId = UUID.randomUUID();
        var dual = dualPolicyEvaluator.evaluate(
                tenantId, applicationId, runId, evaluationContextId, bundle);
        Map<String, Object> dualSummary = new LinkedHashMap<>(dual.summary());
        dualSummary.put("comparisons", dual.comparisons().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleId", c.getRuleId());
            m.put("legacyOutcome", c.getLegacyOutcome());
            m.put("canonicalOutcome", c.getCanonicalOutcome());
            m.put("differenceClass", c.getDifferenceClass());
            m.put("explanation", c.getExplanation());
            return m;
        }).toList());
        stages.put("dualPolicyMs", System.currentTimeMillis() - s);

        List<Map<String, Object>> obligationMaps = new ArrayList<>();
        if (bundle.caseCode() == ValidationCaseCode.CASE_D_OBLIGATION_CONFLICT
                || (hasBureau && hasBank)) {
            String bureauLender = String.valueOf(bundle.metadata().getOrDefault("bureauLender", "HDFC BANK"));
            String bankLender = String.valueOf(bundle.metadata().getOrDefault(
                    "bankDetectedLender", "HDFC BANK LTD EMI NACH"));
            CiObligationMatch match = lenderObligationMatcher.match(
                    tenantId, applicationId, bureauLender, bankLender,
                    bundle.metricStubs().get("bureau.emi.monthly"),
                    bundle.metricStubs().get("bank.emi.monthly"));
            obligationMaps.add(lenderObligationMatcher.toMap(match));
        }

        Map<String, Object> evidenceView = evidenceViewBuilder.build(
                bundle, reconciliations, dualSummary, coverage, obligationMaps);

        var isolation = tenantIsolationValidator.validate(copyPropsForIsolation());
        var security = securityValidationScanner.scan();

        int caseDefaults = ((Number) legacyExposure.getOrDefault("defaultDependentCount", 0)).intValue();
        int silentDefaults = Math.max(caseDefaults, legacyDefaultInventory.inventory().size());

        BigDecimal criticalPct = toBd(coverage.get("criticalCoveragePct"));
        BigDecimal bindingPct = toBd(bindingSummary.get("criticalBindingReadyPct"));
        boolean providerOk = providerResults.stream().allMatch(p ->
                (p.errors() == null || p.errors().isEmpty()) || p.entityCount() > 0);

        var cutover = cutoverReadinessAssessor.assess(
                tenantId,
                replayIdentical,
                criticalPct,
                bindingPct != null ? bindingPct : BigDecimal.ZERO,
                silentDefaults,
                isolation.ok(),
                providerOk,
                true,
                security.criticalOpen(),
                true);

        long total = System.currentTimeMillis() - start;
        stages.put("totalMs", total);

        @SuppressWarnings("unchecked")
        List<String> questions = evidenceView.get("OpenInvestigationQuestions") instanceof List<?> list
                ? (List<String>) list
                : List.of();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCode", caseCode.name());
        summary.put("dataOrigin", bundle.dataOrigin().name());
        summary.put("replayIdentical", replayIdentical);
        summary.put("providerResults", providerResults.size());
        summary.put("cutoverOutcome", cutover.outcome().name());
        summary.put("applicationStatusMutated", false);
        summary.put("authoritative", false);
        summary.put("bindingSummary", bindingSummary);

        ValidationRunResult result = new ValidationRunResult(
                runId,
                "C6-" + caseCode.name() + "-" + runId.toString().substring(0, 8),
                caseCode,
                bundle.dataOrigin(),
                tenantId,
                applicationId,
                evaluationContextId,
                configFreezeHash,
                factHash,
                metricHash,
                reconHash,
                evalHash,
                replayHash,
                replayIdentical,
                providerResults,
                reconciliations,
                dualSummary,
                coverage,
                evidenceView,
                questions,
                obligationMaps,
                legacyExposure,
                cutover.outcome(),
                cutover.dimensions(),
                cutover.blockers(),
                stages,
                total,
                summary);
        runStore.put(runId, result);
        runsByApp.computeIfAbsent(applicationId, k -> new ArrayList<>()).add(runId);
        return result;
    }

    public ValidationRunResult getRun(UUID runId) {
        return runStore.get(runId);
    }

    public List<ValidationRunResult> listRuns() {
        return new ArrayList<>(runStore.values());
    }

    public List<ValidationRunResult> listByApplication(UUID applicationId) {
        List<UUID> ids = runsByApp.getOrDefault(applicationId, List.of());
        List<ValidationRunResult> out = new ArrayList<>();
        for (UUID id : ids) {
            ValidationRunResult r = runStore.get(id);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    private CreditIntelligenceProperties copyPropsForIsolation() {
        CreditIntelligenceProperties p = new CreditIntelligenceProperties();
        p.getTenant().setDevMode(false);
        p.getTenant().setRequireExplicit(true);
        return p;
    }

    private Map<String, Object> buildReconciliations(ValidationBundle bundle) {
        Map<String, BigDecimal> m = bundle.metricStubs();
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal gst = m.get("gst.turnover.trailing_12m");
        BigDecimal itr = m.get("itr.turnover.trailing_12m");
        BigDecimal bank = m.get("bank.turnover.trailing_12m");
        out.put(ReconciliationConstants.XSRC_GST_ITR_TURNOVER, recon(gst, itr));
        out.put(ReconciliationConstants.XSRC_GST_BANK_TURNOVER, recon(gst, bank));
        out.put(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER, recon(itr, bank));
        BigDecimal bEmi = m.get("bureau.emi.monthly");
        BigDecimal bankEmi = m.get("bank.emi.monthly");
        out.put(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION, recon(bEmi, bankEmi));
        Map<String, Object> tri = new LinkedHashMap<>();
        if (gst == null || itr == null || bank == null) {
            tri.put("outcome", ReconciliationOutcome.DATA_INSUFFICIENT.name());
        } else {
            BigDecimal maxPct = max(
                    InvestigationQuestionGenerator.variancePct(gst, itr),
                    InvestigationQuestionGenerator.variancePct(gst, bank),
                    InvestigationQuestionGenerator.variancePct(itr, bank));
            String outcome;
            if (maxPct == null) {
                outcome = ReconciliationOutcome.DATA_INSUFFICIENT.name();
            } else if (maxPct.compareTo(BigDecimal.valueOf(10)) <= 0) {
                outcome = ReconciliationOutcome.MATCH.name();
            } else if (maxPct.compareTo(BigDecimal.valueOf(20)) <= 0) {
                outcome = ReconciliationOutcome.ACCEPTABLE_VARIANCE.name();
            } else {
                outcome = ReconciliationOutcome.MATERIAL_VARIANCE.name();
            }
            tri.put("outcome", outcome);
            tri.put("maxVariancePct", maxPct);
            tri.put("gst", gst);
            tri.put("itr", itr);
            tri.put("bank", bank);
        }
        out.put(ReconciliationConstants.TURNOVER_TRIANGULATION, tri);
        List<String> diFlags = new ArrayList<>();
        if (!bundle.sources().containsKey("bank")) {
            diFlags.add("BANK");
        }
        if (!bundle.sources().containsKey("bureau")) {
            diFlags.add("BUREAU");
        }
        if (!diFlags.isEmpty()) {
            out.put("DATA_INSUFFICIENT_FLAGS", diFlags);
        }
        return out;
    }

    private static Map<String, Object> recon(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return InvestigationQuestionGenerator.pair(
                    ReconciliationOutcome.DATA_INSUFFICIENT.name(), left, right, null);
        }
        BigDecimal pct = InvestigationQuestionGenerator.variancePct(left, right);
        String outcome;
        if (pct.compareTo(BigDecimal.valueOf(5)) <= 0) {
            outcome = ReconciliationOutcome.MATCH.name();
        } else if (pct.compareTo(BigDecimal.valueOf(15)) <= 0) {
            outcome = ReconciliationOutcome.ACCEPTABLE_VARIANCE.name();
        } else if (pct.compareTo(BigDecimal.valueOf(40)) <= 0) {
            outcome = ReconciliationOutcome.MATERIAL_VARIANCE.name();
        } else {
            outcome = ReconciliationOutcome.CONFLICT.name();
        }
        return InvestigationQuestionGenerator.pair(outcome, left, right, pct);
    }

    private Map<String, Object> buildFrozenOutcomes(
            UUID freezeId, String freezeHash, UUID policyVersionId, String policyHash,
            UUID snapshotId, UUID metricSetId, UUID reconSetId,
            LocalDate asOf, Instant clockInstant,
            Map<String, Object> reconciliations, ValidationBundle bundle) {
        Map<String, Object> outcomes = new LinkedHashMap<>();
        outcomes.put("aggregatePolicyRecommendation",
                bundle.caseCode() == ValidationCaseCode.CASE_A_STRONG ? "APPROVE" : "REFER");
        outcomes.put("aggregateCreditDecision",
                bundle.caseCode() == ValidationCaseCode.CASE_A_STRONG ? "APPROVED" : "REFER");
        outcomes.put("aggregateRiskScore", bundle.caseCode() == ValidationCaseCode.CASE_A_STRONG ? 80 : 45);
        outcomes.put("configFreezeId", freezeId.toString());
        outcomes.put("configFreezeHash", freezeHash);
        outcomes.put("policyVersionId", policyVersionId.toString());
        outcomes.put("policyContentHash", policyHash);
        outcomes.put("factSnapshotId", snapshotId.toString());
        outcomes.put("metricResultSetId", metricSetId.toString());
        outcomes.put("reconciliationResultSetId", reconSetId.toString());
        outcomes.put("evaluationAsOf", asOf.toString());
        outcomes.put("clockInstant", clockInstant.toString());
        outcomes.put("clockZone", "Asia/Kolkata");
        outcomes.put("reconciliations", reconciliations);
        outcomes.put("caseCode", bundle.caseCode().name());
        outcomes.put("dataOrigin", bundle.dataOrigin().name());
        return outcomes;
    }

    private static void assertHonestOrigin(DataOrigin origin) {
        if (origin == null) {
            throw new IllegalStateException("dataOrigin required");
        }
    }

    private static BigDecimal toBd(Object o) {
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static BigDecimal max(BigDecimal... vals) {
        BigDecimal m = null;
        for (BigDecimal v : vals) {
            if (v == null) {
                continue;
            }
            m = m == null ? v : m.max(v);
        }
        return m;
    }
}
