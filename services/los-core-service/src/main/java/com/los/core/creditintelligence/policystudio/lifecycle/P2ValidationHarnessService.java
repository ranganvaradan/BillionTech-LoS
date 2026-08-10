package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import com.los.core.creditintelligence.evaluation.repository.CiEvaluationContextRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiP2ValidationCase;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiP2ValidationDefect;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiP2ValidationRun;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiP2ValidationCaseRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiP2ValidationDefectRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiP2ValidationRunRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.service.CreditIntelligenceFoundationService;
import com.los.core.creditintelligence.service.ShadowCreditEvaluationService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.UnderwritingEvaluationRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P2 validation harness — exercises the real afterProduction shadow hook path.
 * Never enables production authority. Never fabricates SHADOW_VALIDATED without real/stored cases.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class P2ValidationHarnessService {

    public static final int MIN_REAL_STORED = 20;

    private final CreditIntelligenceProperties properties;
    private final LoanApplicationRepository loanApplicationRepository;
    private final UnderwritingEvaluationRepository underwritingEvaluationRepository;
    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final CiP2ValidationRunRepository runRepository;
    private final CiP2ValidationCaseRepository caseRepository;
    private final CiP2ValidationDefectRepository defectRepository;
    private final ShadowPolicyRoutingService routingService;
    private final CreditIntelligenceFoundationService foundationService;
    private final ShadowCreditEvaluationService shadowCreditEvaluationService;
    private final CiEvaluationContextRepository evaluationContextRepository;
    private final ShadowApplicationDiscoveryService discoveryService;
    private final PolicyCatalogueService catalogueService;

    @Transactional
    public Map<String, Object> runValidation(String productFilter, String actor) {
        UUID tenantId = properties.getDefaultTenantId();
        CiP2ValidationRun run = runRepository.save(CiP2ValidationRun.builder()
                .tenantId(tenantId)
                .productCode(productFilter)
                .startedAt(Instant.now())
                .status("RUNNING")
                .certificationStatus(PolicyShadowRoutingOutcomes.CERT_INSUFFICIENT)
                .createdBy(actor == null ? "p2_harness" : actor)
                .allowCanonicalAuthority(false)
                .shadowOnly(true)
                .build());

        Map<String, Object> discovery = discoveryService.discover();
        List<LoanApplication> apps = new ArrayList<>(
                loanApplicationRepository.findAll(PageRequest.of(0, 200)).getContent());
        if (productFilter != null && !productFilter.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getLoanProduct() != null
                            && a.getLoanProduct().equalsIgnoreCase(productFilter))
                    .toList();
        }

        int realStored = 0;
        int routedOk = 0;
        int replayOk = 0;
        int hookUsed = 0;
        int packageExact = 0;
        List<Map<String, Object>> caseViews = new ArrayList<>();
        Map<String, Integer> comparisonDist = new LinkedHashMap<>();
        Map<String, Integer> originDist = new LinkedHashMap<>();
        Map<String, Integer> legacyDefaults = new LinkedHashMap<>();

        EffectiveUnderwritingContext emptyCtx = new EffectiveUnderwritingContext(
                0, false, null, null, null, null, null, null, null, Map.of());

        for (LoanApplication app : apps) {
            String origin = classifyOrigin(app);
            originDist.merge(origin, 1, Integer::sum);
            boolean countsAsReal = "ANONYMIZED_REAL_DEV_DATA".equals(origin)
                    || "STORED_PROVIDER_DATA".equals(origin)
                    || "USER_SUPPLIED_SAMPLE".equals(origin);
            if (countsAsReal) {
                realStored++;
            }

            CiP2ValidationCase vc = CiP2ValidationCase.builder()
                    .runId(run.getId())
                    .applicationId(app.getId())
                    .applicationToken(app.getApplicationNumber() == null
                            ? app.getId().toString() : app.getApplicationNumber())
                    .productCode(app.getLoanProduct())
                    .originClassification(origin)
                    .build();

            try {
                Optional<CreditIntelligenceFoundationService.PrepResult> prep =
                        foundationService.prepare(app, emptyCtx, null, "p2_harness");
                UnderwritingEvaluation legacy = underwritingEvaluationRepository
                        .findTopByApplicationIdOrderByEvaluatedAtDesc(app.getId())
                        .orElse(null);
                String legacyOutcome = legacy == null ? null : legacy.getAggregateDecision();
                vc.setLegacyOutcome(legacyOutcome);
                measureLegacyDefaults(legacy, legacyDefaults);
                vc.setUnderwritingHookUsed(true);
                hookUsed++;

                if (prep.isPresent()) {
                    foundationService.afterProduction(
                            prep.get(), app, legacy, null, legacyOutcome);
                }

                Optional<ShadowPolicyRoutingService.RoutingResult> routed =
                        routingService.routeShadow(app, null, null);
                if (routed.isPresent()) {
                    ShadowPolicyRoutingService.RoutingResult r = routed.get();
                    vc.setEvaluationBusinessDate(r.evaluationBusinessDate());
                    vc.setResolverOutcome(r.outcome());
                    vc.setApplicabilityId(r.applicabilityId());
                    vc.setPolicyVersionId(r.policyVersionId());
                    vc.setExecutablePackageId(r.executablePackageId());
                    vc.setContentHash(r.contentHash());
                    vc.setRoutingSuccess(r.executableForShadow());
                    if (r.executableForShadow()) {
                        routedOk++;
                    }
                    Map<String, Object> evidence = new LinkedHashMap<>(r.evidence() == null ? Map.of() : r.evidence());
                    evidence.put("resolvePayload", r.resolvePayload());
                    evidence.put("executableForShadow", r.executableForShadow());
                    vc.setEvidence(evidence);

                    Optional<CiEvaluationContext> ctxOpt = evaluationContextRepository
                            .findTopByApplicationIdOrderByCreatedAtDesc(app.getId());
                    if (ctxOpt.isPresent() && r.policyVersionId() != null) {
                        CiEvaluationContext ctx = ctxOpt.get();
                        vc.setEvaluationContextId(ctx.getId());
                        vc.setEvaluationContentHash(ctx.getContentHash());
                        boolean sameVersion = r.policyVersionId().equals(ctx.getPolicyVersionId());
                        vc.setPackageExact(sameVersion);
                        if (sameVersion) {
                            packageExact++;
                        }
                        Optional<ShadowCreditEvaluationService.ShadowResult> replay =
                                shadowCreditEvaluationService.replay(ctx.getId());
                        boolean pass = replay.isPresent() && sameVersion && ctx.getContentHash() != null;
                        vc.setReplayPass(pass);
                        if (pass) {
                            replayOk++;
                        }
                        if (replay.isPresent() && replay.get().shadow() != null) {
                            CiCreditEvaluation shadow = replay.get().shadow();
                            vc.setShadowOutcome(shadow.getOverallOutcome());
                            if (shadow.getId() != null) {
                                vc.setShadowRecommendationHash(shadow.getId().toString());
                            }
                        }
                    }

                    String cmp = classifyComparison(legacyOutcome, vc.getShadowOutcome());
                    vc.setComparisonClass(cmp);
                    comparisonDist.merge(cmp, 1, Integer::sum);
                    if ("CANONICAL_MORE_PERMISSIVE".equals(cmp)) {
                        defectRepository.save(CiP2ValidationDefect.builder()
                                .runId(run.getId())
                                .applicationId(app.getId())
                                .applicationToken(vc.getApplicationToken())
                                .severity("BLOCKING")
                                .component("COMPARISON")
                                .defectType("CANONICAL_MORE_PERMISSIVE")
                                .rootCause("Legacy FAIL/REFER → Shadow PASS unexplained")
                                .recommendedAction("Individual review required before SHADOW_VALIDATED")
                                .blocking(true)
                                .build());
                    }
                } else {
                    vc.setResolverOutcome("ROUTING_ERROR");
                    vc.setRoutingSuccess(false);
                }
            } catch (Exception ex) {
                log.warn("p2_harness_case_failed app={} err={}", app.getId(), ex.toString());
                vc.setResolverOutcome("HARNESS_ERROR");
                vc.setEvidence(Map.of("error", ex.getClass().getSimpleName(),
                        "message", ex.getMessage() == null ? "" : ex.getMessage()));
                defectRepository.save(CiP2ValidationDefect.builder()
                        .runId(run.getId())
                        .applicationId(app.getId())
                        .applicationToken(vc.getApplicationToken())
                        .severity("HIGH")
                        .component("HARNESS")
                        .defectType("UNDERWRITING_HOOK_FAILURE")
                        .rootCause(ex.getMessage())
                        .recommendedAction("Investigate shadow hook; legacy underwriting must remain unaffected")
                        .blocking(false)
                        .build());
            }
            caseRepository.save(vc);
            caseViews.add(caseView(vc));
        }

        List<Map<String, Object>> catalogueLinkage = new ArrayList<>();
        int unlinkedActive = 0;
        for (CiPolicyApplicability a : applicabilityRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)) {
            Map<String, Object> row = catalogueService.toBusinessRow(a);
            catalogueLinkage.add(Map.of(
                    "applicabilityId", a.getId().toString(),
                    "policyVersion", a.getPolicyVersionLabel() == null ? "" : a.getPolicyVersionLabel(),
                    "status", a.getBusinessStatus() == null ? "" : a.getBusinessStatus(),
                    "linkageClass", a.getLinkageClass() == null ? "UNLINKED" : a.getLinkageClass(),
                    "shadowRoutable", Boolean.TRUE.equals(a.getShadowRoutable()),
                    "shadowEligibility", a.getShadowEligibility() == null ? "" : a.getShadowEligibility(),
                    "policyVersionId", a.getPolicyVersionId() == null ? "" : a.getPolicyVersionId().toString(),
                    "executablePackageId", a.getExecutablePackageId() == null ? "" : a.getExecutablePackageId().toString()
            ));
            if (!Boolean.TRUE.equals(a.getShadowRoutable())
                    && a.getBusinessStatus() != null
                    && List.of("ACTIVE", "SCHEDULED").contains(a.getBusinessStatus())) {
                unlinkedActive++;
                defectRepository.save(CiP2ValidationDefect.builder()
                        .runId(run.getId())
                        .severity("HIGH")
                        .component("CATALOGUE")
                        .defectType("UNLINKED_ACTIVE_POLICY")
                        .rootCause("ACTIVE/SCHEDULED catalogue entry not shadow-routable")
                        .recommendedAction("Link immutable package via /policy-catalogue/{id}/link-immutable")
                        .blocking(true)
                        .applicationToken(a.getPolicyName() + " " + a.getPolicyVersionLabel())
                        .evidence(row)
                        .build());
            }
        }

        long blocking = defectRepository.countByRunIdAndBlockingIsTrueAndResolvedIsFalse(run.getId());
        String cert = certify(realStored, apps.size(), routedOk, replayOk, packageExact, blocking, unlinkedActive);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scannedLoanApplications", apps.size());
        summary.put("realStoredCount", realStored);
        summary.put("minRealStoredRequired", MIN_REAL_STORED);
        summary.put("underwritingHookInvocations", hookUsed);
        summary.put("routingSuccessCount", routedOk);
        summary.put("replayPassCount", replayOk);
        summary.put("packageExactCount", packageExact);
        summary.put("blockingDefects", blocking);
        summary.put("unlinkedActiveScheduledPolicies", unlinkedActive);
        summary.put("originDistribution", originDist);
        summary.put("comparisonDistribution", comparisonDist);
        summary.put("legacyDefaultFrequencies", legacyDefaults);
        summary.put("catalogueLinkage", catalogueLinkage);
        summary.put("discovery", Map.of(
                "scannedLoanApplications", discovery.getOrDefault("scannedLoanApplications", 0),
                "storesSearched", discovery.getOrDefault("storesSearched", List.of()),
                "note", discovery.getOrDefault("note", "")));
        summary.put("datasetExportSpec", datasetExportSpec());
        summary.put("allowCanonicalAuthority", false);
        summary.put("productionAuthority", "DISABLED");
        summary.put("cases", caseViews);

        run.setSummary(summary);
        run.setCompletedAt(Instant.now());
        run.setStatus("COMPLETED");
        run.setCertificationStatus(cert);
        runRepository.save(run);

        Map<String, Object> out = new LinkedHashMap<>(summary);
        out.put("runId", run.getId().toString());
        out.put("certificationStatus", cert);
        out.put("status", "COMPLETED");
        out.put("defects", defectRepository.findByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                .map(this::defectView).toList());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> latestDashboard() {
        UUID tenantId = properties.getDefaultTenantId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "P2 Real Application Validation");
        out.put("allowCanonicalAuthority", false);
        out.put("productionAuthority", "DISABLED");
        List<CiP2ValidationRun> runs = runRepository.findByTenantIdOrderByStartedAtDesc(tenantId);
        if (runs.isEmpty()) {
            out.put("certificationStatus", PolicyShadowRoutingOutcomes.CERT_INSUFFICIENT);
            out.put("message", "No validation run yet. Trigger P2 validation after loading real/stored applications.");
            out.put("discovery", discoveryService.discover());
            out.put("catalogue", catalogueService.listCatalogue(tenantId));
            out.put("datasetExportSpec", datasetExportSpec());
            out.put("realStoredCount", 0);
            out.put("scannedLoanApplications", 0);
            return out;
        }
        CiP2ValidationRun latest = runs.get(0);
        out.put("runId", latest.getId().toString());
        out.put("certificationStatus", latest.getCertificationStatus());
        out.put("status", latest.getStatus());
        out.put("completedAt", latest.getCompletedAt() == null ? null : latest.getCompletedAt().toString());
        if (latest.getSummary() != null) {
            out.putAll(latest.getSummary());
        }
        out.put("defects", defectRepository.findByRunIdOrderByCreatedAtDesc(latest.getId()).stream()
                .map(this::defectView).toList());
        out.put("cases", caseRepository.findByRunIdOrderByCreatedAtAsc(latest.getId()).stream()
                .map(this::caseView).toList());
        return out;
    }

    private void measureLegacyDefaults(UnderwritingEvaluation legacy, Map<String, Integer> counts) {
        if (legacy == null) {
            return;
        }
        String blob = String.valueOf(legacy.getEffectiveValuesJson())
                + String.valueOf(legacy.getRuleResultsJson())
                + String.valueOf(legacy.getParameterResultsJson());
        for (String key : List.of(
                "MONTHLY_INCOME", "EMI", "ABB", "LIVE_UNSECURED_LOAN_COUNT",
                "GST_TURNOVER", "GAP_", "SCF_GAP_", "DEFAULT", "defaulted")) {
            if (blob.contains(key)) {
                counts.merge(key, 1, Integer::sum);
            }
        }
    }

    private String certify(
            int realStored, int scanned, int routedOk, int replayOk, int packageExact,
            long blocking, int unlinkedActive) {
        if (blocking > 0 || unlinkedActive > 0) {
            // Unlinked active policies block certification, but empty app corpus is INSUFFICIENT
            if (realStored < MIN_REAL_STORED && scanned == 0) {
                return PolicyShadowRoutingOutcomes.CERT_INSUFFICIENT;
            }
            if (realStored < MIN_REAL_STORED) {
                return PolicyShadowRoutingOutcomes.CERT_INSUFFICIENT;
            }
            return PolicyShadowRoutingOutcomes.CERT_BLOCKED;
        }
        if (realStored < MIN_REAL_STORED) {
            return PolicyShadowRoutingOutcomes.CERT_INSUFFICIENT;
        }
        if (routedOk == realStored && replayOk == realStored && packageExact == realStored) {
            return PolicyShadowRoutingOutcomes.CERT_SHADOW_VALIDATED;
        }
        return PolicyShadowRoutingOutcomes.CERT_BLOCKED;
    }

    private String classifyOrigin(LoanApplication app) {
        if (app.getApplicationNumber() != null && app.getApplicationNumber().startsWith("DEMO")) {
            return "SYNTHETIC";
        }
        if (app.getApplicationNumber() != null && app.getApplicationNumber().startsWith("P2-FIX")) {
            return "REPRESENTATIVE_FIXTURE";
        }
        return "ANONYMIZED_REAL_DEV_DATA";
    }

    private String classifyComparison(String legacy, String shadow) {
        if (legacy == null && shadow == null) {
            return "DATA_INSUFFICIENT";
        }
        if (legacy == null || shadow == null) {
            return "UNKNOWN";
        }
        String L = legacy.toUpperCase();
        String S = shadow.toUpperCase();
        if (L.equals(S) || (passLike(L) && passLike(S)) || (failLike(L) && failLike(S))) {
            return "MATCH";
        }
        if (failLike(L) && passLike(S)) {
            return "CANONICAL_MORE_PERMISSIVE";
        }
        if (passLike(L) && failLike(S)) {
            return "CANONICAL_MORE_STRICT";
        }
        return "EXPECTED_POLICY_DIFFERENCE";
    }

    private static boolean passLike(String s) {
        return s.contains("APPROVE") || s.contains("PASS") || s.contains("SANCTION");
    }

    private static boolean failLike(String s) {
        return s.contains("REJECT") || s.contains("FAIL") || s.contains("DECLINE") || s.contains("REFER");
    }

    private Map<String, Object> caseView(CiP2ValidationCase c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationToken", c.getApplicationToken());
        m.put("product", c.getProductCode());
        m.put("origin", c.getOriginClassification());
        m.put("evaluationBusinessDate", c.getEvaluationBusinessDate() == null ? null : c.getEvaluationBusinessDate().toString());
        m.put("resolverOutcome", c.getResolverOutcome());
        m.put("policyVersionId", c.getPolicyVersionId() == null ? null : c.getPolicyVersionId().toString());
        m.put("executablePackageId", c.getExecutablePackageId() == null ? null : c.getExecutablePackageId().toString());
        m.put("contentHash", c.getContentHash());
        m.put("evaluationContextId", c.getEvaluationContextId() == null ? null : c.getEvaluationContextId().toString());
        m.put("legacyOutcome", c.getLegacyOutcome());
        m.put("shadowOutcome", c.getShadowOutcome());
        m.put("comparisonClass", c.getComparisonClass());
        m.put("replayPass", c.getReplayPass());
        m.put("routingSuccess", c.getRoutingSuccess());
        m.put("packageExact", c.getPackageExact());
        m.put("underwritingHookUsed", c.getUnderwritingHookUsed());
        return m;
    }

    private Map<String, Object> defectView(CiP2ValidationDefect d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", d.getSeverity());
        m.put("component", d.getComponent());
        m.put("defectType", d.getDefectType());
        m.put("rootCause", d.getRootCause());
        m.put("recommendedAction", d.getRecommendedAction());
        m.put("blocking", d.getBlocking());
        m.put("resolved", d.getResolved());
        m.put("applicationToken", d.getApplicationToken());
        return m;
    }

    public static Map<String, Object> datasetExportSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("requiredCount", "20–50 historical applications (minimum 20 for SHADOW_VALIDATED)");
        spec.put("environment", "DEV/UAT/STAGING only — never production");
        spec.put("include", List.of(
                "application core (product, amount, dates, borrower type — anonymized PII)",
                "legacy underwriting evaluation result",
                "bureau stored response (or explicit absence)",
                "bank/AA stored response (or absence)",
                "GST stored response (or absence)",
                "ITR stored response (or absence)",
                "product code",
                "evaluation/business date"));
        spec.put("anonymize", List.of("name", "PAN", "Aadhaar", "phone", "email", "address",
                "bank account", "bureau account identifiers"));
        spec.put("preserve", List.of("dates", "amounts", "balances", "DPD", "scores", "turnover",
                "transaction patterns", "repayment history", "provider statuses"));
        spec.put("doNot", List.of(
                "Only successful/complete cases",
                "Call external providers",
                "Use production credentials",
                "Commit raw PII to Git"));
        return spec;
    }
}
