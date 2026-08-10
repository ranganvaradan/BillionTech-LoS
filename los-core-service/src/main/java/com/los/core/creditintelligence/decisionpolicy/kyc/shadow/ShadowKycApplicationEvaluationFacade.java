package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.decisionpolicy.sim.ExactExecutablePackageLoader;
import com.los.core.creditintelligence.decisionpolicy.sim.ExactPackageCertification;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.ShadowPolicyRoutingService;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-facing KYC-5 entry: resolve exact catalogue package (or explicit demo fixture), evaluate shadow.
 * Production computeKycOutcome is read for comparison only — never written.
 * Catalogue-linked packages NEVER fall back to golden fixture content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowKycApplicationEvaluationFacade {

    private final CreditIntelligenceProperties properties;
    private final ShadowKycPolicyEvaluationService evaluationService;
    private final ExactExecutablePackageLoader packageLoader;
    private final ObjectProvider<LoanApplicationRepository> applicationRepository;
    private final ObjectProvider<KycStepResultRepository> stepResultRepository;
    private final ObjectProvider<IKycOrchestrationService> kycOrchestration;
    private final ObjectProvider<ShadowPolicyRoutingService> routingService;
    private final ObjectProvider<ActiveWorkflowConfigService> workflowConfigService;
    private final ObjectProvider<CiKycPolicyEvaluationRepository> evaluationRepository;

    /**
     * Safe after-KYC hook. Swallows all errors. Does not mutate application status.
     */
    public void afterKycWorkflowSafe(UUID applicationId) {
        try {
            if (!isKycShadowEnabled()) {
                return;
            }
            if (properties.getCutover() != null && properties.getCutover().isAllowCanonicalAuthority()) {
                log.warn("kyc_shadow_refused allowCanonicalAuthority unexpectedly true");
                return;
            }
            evaluateForApplication(applicationId, null, true);
        } catch (Exception ex) {
            log.warn("kyc_shadow_after_kyc_failed app={} err={} — production KYC unchanged",
                    applicationId, ex.getMessage());
        }
    }

    public Map<String, Object> evaluateForApplication(
            UUID applicationId,
            CiExecutablePolicyPackage packageOverride,
            boolean persist
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("shadow", true);
        safety.put("authoritative", false);
        safety.put("allowCanonicalAuthority", false);
        safety.put("banner", "SHADOW — DOES NOT AFFECT APPLICATION");

        LoanApplicationRepository apps = applicationRepository.getIfAvailable();
        if (apps == null || applicationId == null) {
            safety.put("status", "BLOCKED");
            safety.put("reason", "Application repository unavailable");
            return safety;
        }
        LoanApplication app = apps.findById(applicationId).orElse(null);
        if (app == null) {
            safety.put("status", "BLOCKED");
            safety.put("reason", "Application not found");
            return safety;
        }

        String productionOutcome = null;
        try {
            IKycOrchestrationService orch = kycOrchestration.getIfAvailable();
            if (orch != null) {
                Map<String, Object> computed = orch.computeKycOutcome(applicationId);
                if (computed != null && computed.get("outcome") != null) {
                    productionOutcome = String.valueOf(computed.get("outcome"));
                } else if (computed != null && computed.get("kycOutcome") != null) {
                    productionOutcome = String.valueOf(computed.get("kycOutcome"));
                } else if (computed != null && computed.get("status") != null) {
                    productionOutcome = String.valueOf(computed.get("status"));
                }
            }
        } catch (Exception ex) {
            log.warn("kyc_shadow_production_read_failed app={} err={}", applicationId, ex.getMessage());
        }

        List<NormalizedKycFactBuilder.StepEvidence> steps = loadSteps(applicationId);
        Map<String, Object> appFields = applicationFields(app);
        Map<String, Object> hints = applicationHints(app, appFields);
        Map<String, Object> workflowProv = workflowProvenance(app);
        List<Object> evidenceRefs = evidenceRefs(applicationId);

        ShadowPolicyRoutingService.RoutingResult routed = null;
        ShadowPolicyRoutingService routing = routingService.getIfAvailable();
        if (routing != null && packageOverride == null) {
            routed = routing.routeShadow(app, LocalDate.now(), null).orElse(null);
        }

        // Catalogue EXACTLY_ONE → load exact package; never substitute golden
        boolean allowDemoWhenNoCatalogue = packageOverride == null
                && (routed == null || !routed.executableForShadow() || routed.executablePackageId() == null);
        ExactPackageLoadResult load = packageLoader.resolve(
                routed, packageOverride, allowDemoWhenNoCatalogue, tenantId());

        if (!load.ok()) {
            Map<String, Object> blocked = new LinkedHashMap<>(safety);
            blocked.putAll(load.toMap());
            blocked.put("status", "BLOCKED");
            blocked.put("applicationId", applicationId.toString());
            if (ExactPackageCertification.PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION.equals(load.code())
                    || ExactPackageCertification.SIMULATION_CERTIFICATION_FAILURE.equals(load.code())
                    || ExactPackageCertification.PACKAGE_NOT_FOUND.equals(load.code())) {
                blocked.put("code", load.code());
            }
            // Still allow KYC evaluate path for NO_APPLICABLE / AMBIGUOUS to record blocked routing
            if (PolicyApplicabilityResolver.NO_APPLICABLE_POLICY.equals(load.code())
                    || PolicyApplicabilityResolver.AMBIGUOUS_POLICY_CONFIGURATION.equals(load.code())) {
                CiExecutablePolicyPackage demo = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenantId());
                Map<String, Object> routedBlocked = evaluationService.evaluate(
                        ShadowKycEvaluationRequest.builder()
                                .tenantId(tenantId())
                                .applicationId(applicationId)
                                .policyPackage(demo)
                                .routingOutcome(load.code())
                                .routingReason(load.reason())
                                .stepEvidence(steps)
                                .applicationFields(appFields)
                                .applicationHints(hints)
                                .productionKycOutcome(productionOutcome)
                                .evaluationBusinessDate(LocalDate.now())
                                .workflowProvenance(workflowProv)
                                .evidenceRefs(evidenceRefs)
                                .persist(false)
                                .build());
                routedBlocked.putAll(safety);
                return routedBlocked;
            }
            return blocked;
        }

        CiExecutablePolicyPackage pkg = load.pkg();
        String routingOutcome = load.routingOutcome() != null ? load.routingOutcome()
                : (routed != null ? routed.outcome() : "EXACTLY_ONE");
        String routingReason = routed != null ? routed.reason() : load.reason();
        if (load.demoFixture()) {
            workflowProv = new LinkedHashMap<>(workflowProv);
            workflowProv.put("fixturePackageUsed", true);
            workflowProv.put("note", "Explicit VALIDATION FIXTURE package — not a catalogue substitute");
        } else if (routed != null) {
            workflowProv = new LinkedHashMap<>(workflowProv);
            workflowProv.put("catalogueExecutablePackageId", routed.executablePackageId());
            workflowProv.put("cataloguePolicyVersion", routed.policyVersion());
            workflowProv.put("fixturePackageUsed", false);
            workflowProv.put("exactPackageLoaded", true);
            workflowProv.put("contentHash", pkg.getContentHash());
        }

        Map<String, Object> result = evaluationService.evaluate(
                ShadowKycEvaluationRequest.builder()
                        .tenantId(tenantId())
                        .applicationId(applicationId)
                        .policyPackage(pkg)
                        .routingOutcome(routingOutcome)
                        .routingReason(routingReason)
                        .stepEvidence(steps)
                        .applicationFields(appFields)
                        .applicationHints(hints)
                        .productionKycOutcome(productionOutcome)
                        .evaluationBusinessDate(LocalDate.now())
                        .workflowProvenance(workflowProv)
                        .evidenceRefs(evidenceRefs)
                        .persist(persist)
                        .build());
        result.putAll(safety);
        result.put("applicationId", applicationId.toString());
        result.put("exactPackageLoaded", !load.demoFixture());
        result.put("demoFixture", load.demoFixture());
        return result;
    }

    public Map<String, Object> latestForApplication(UUID applicationId) {
        CiKycPolicyEvaluationRepository repo = evaluationRepository.getIfAvailable();
        if (repo == null) {
            return Map.of("shadow", true, "authoritative", false, "message", "No repository");
        }
        return repo.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .map(this::toView)
                .orElse(Map.of(
                        "shadow", true,
                        "authoritative", false,
                        "allowCanonicalAuthority", false,
                        "message", "No shadow KYC evaluation recorded yet.",
                        "banner", "SHADOW — DOES NOT AFFECT APPLICATION"));
    }

    public Map<String, Object> replayLatest(UUID applicationId) {
        CiKycPolicyEvaluationRepository repo = evaluationRepository.getIfAvailable();
        if (repo == null) {
            return Map.of("replayMatch", false, "reason", "No repository");
        }
        Optional<CiKycPolicyEvaluation> prior = repo.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (prior.isEmpty()) {
            return Map.of("replayMatch", false, "reason", "No prior evaluation");
        }
        CiKycPolicyEvaluation e = prior.get();
        ExactPackageLoadResult load = packageFromStored(e);
        if (!load.ok()) {
            Map<String, Object> fail = new LinkedHashMap<>(load.toMap());
            fail.put("replayMatch", false);
            return fail;
        }
        Map<String, Object> priorView = toView(e);
        return evaluationService.replay(priorView, load.pkg());
    }

    private ExactPackageLoadResult packageFromStored(CiKycPolicyEvaluation e) {
        if (e.getExecutablePackageId() != null) {
            ExactPackageLoadResult loaded = packageLoader.loadById(
                    e.getExecutablePackageId(), e.getPolicyContentHash());
            if (loaded.ok()) {
                return loaded;
            }
            // Catalogue id present but unloadable — never silent golden substitute
            if (!ExactPackageCertification.PACKAGE_NOT_FOUND.equals(loaded.code())
                    || e.getExecutablePackageId() != null) {
                // If package missing from DB but recorded hash was a known demo fixture code, allow rebuild
                if (ExactPackageCertification.PACKAGE_NOT_FOUND.equals(loaded.code())
                        && isKnownDemoPolicyCode(e.getPolicyCode())) {
                    return ExactPackageLoadResult.ok(rebuildDemo(e), true);
                }
                return loaded;
            }
        }
        if (isKnownDemoPolicyCode(e.getPolicyCode())) {
            return ExactPackageLoadResult.ok(rebuildDemo(e), true);
        }
        return ExactPackageLoadResult.notFound(e.getExecutablePackageId());
    }

    private static boolean isKnownDemoPolicyCode(String code) {
        return "DECISION_POLICY_KYC_V1".equals(code)
                || "DECISION_POLICY_KYC_V2".equals(code)
                || (code != null && (code.contains("VALIDATION") || code.startsWith("DEMO_")));
    }

    private CiExecutablePolicyPackage rebuildDemo(CiKycPolicyEvaluation e) {
        if ("DECISION_POLICY_KYC_V2".equals(e.getPolicyCode()) || "2".equals(e.getPolicyVersion())) {
            return GoldenKycShadowPackageFactory.decisionPolicyKycV2(tenantId());
        }
        if ("DECISION_POLICY_E2E_V2".equals(e.getPolicyCode())) {
            return com.los.core.creditintelligence.decisionpolicy.sim.GoldenDecisionPolicyE2EPackageFactory
                    .decisionPolicyE2eV2(tenantId());
        }
        if ("DECISION_POLICY_E2E_V1".equals(e.getPolicyCode())
                || (e.getPolicyCode() != null && e.getPolicyCode().contains("E2E"))) {
            return com.los.core.creditintelligence.decisionpolicy.sim.GoldenDecisionPolicyE2EPackageFactory
                    .decisionPolicyE2eV1(tenantId());
        }
        return GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenantId());
    }

    private Map<String, Object> toView(CiKycPolicyEvaluation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("applicationId", e.getApplicationId());
        m.put("overallOutcome", e.getOverallOutcome());
        m.put("productionKycOutcome", e.getProductionKycOutcome());
        m.put("comparisonClass", e.getComparisonClass());
        m.put("reviewRequired", e.isReviewRequired());
        m.put("reviewReason", e.getReviewReason());
        m.put("policyCode", e.getPolicyCode());
        m.put("policyVersion", e.getPolicyVersion());
        m.put("executablePackageId", e.getExecutablePackageId());
        m.put("policyContentHash", e.getPolicyContentHash());
        m.put("deterministicHash", e.getDeterministicHash());
        m.put("frozenFacts", e.getFrozenFacts());
        m.put("applicationInputs", e.getApplicationInputs());
        m.put("ruleResults", e.getRuleResults());
        m.put("comparison", e.getComparison());
        m.put("referPayload", e.getReferPayload());
        m.put("workflowProvenance", e.getWorkflowProvenance());
        m.put("missingFacts", e.getMissingFacts());
        m.put("failReasons", e.getFailReasons());
        m.put("referReasons", e.getReferReasons());
        m.put("evaluationBusinessDate", e.getEvaluationBusinessDate());
        m.put("shadow", true);
        m.put("authoritative", false);
        m.put("allowCanonicalAuthority", false);
        m.put("certificationStatus", e.getCertificationStatus());
        m.put("banner", "SHADOW — DOES NOT AFFECT APPLICATION");
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private List<NormalizedKycFactBuilder.StepEvidence> loadSteps(UUID applicationId) {
        KycStepResultRepository repo = stepResultRepository.getIfAvailable();
        if (repo == null) {
            return List.of();
        }
        List<KycStepResult> results = repo.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        List<NormalizedKycFactBuilder.StepEvidence> steps = new ArrayList<>();
        for (KycStepResult r : results) {
            NormalizedKycFactBuilder.StepEvidence ev = NormalizedKycFactBuilder.StepEvidence.of(r);
            if (ev != null) {
                steps.add(ev);
            }
        }
        return steps;
    }

    private List<Object> evidenceRefs(UUID applicationId) {
        KycStepResultRepository repo = stepResultRepository.getIfAvailable();
        if (repo == null) {
            return List.of();
        }
        List<Object> refs = new ArrayList<>();
        for (KycStepResult r : repo.findByApplicationIdOrderByCreatedAtAsc(applicationId)) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", "KycStepResult");
            ref.put("id", r.getId());
            ref.put("stepType", r.getStepType() == null ? null : r.getStepType().name());
            ref.put("outcome", r.getOutcome() == null ? null : r.getOutcome().name());
            refs.add(ref);
        }
        return refs;
    }

    private Map<String, Object> applicationFields(LoanApplication app) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (app.getRequestedAmount() != null) {
            m.put("requested_amount", app.getRequestedAmount());
        }
        if (app.getBorrowerType() != null) {
            m.put("borrower_type", app.getBorrowerType().name());
        }
        Map<String, Object> personal = app.getPersonalInfo() == null ? Map.of() : app.getPersonalInfo();
        Map<String, Object> business = app.getBusinessInfo() == null ? Map.of() : app.getBusinessInfo();
        Object name = personal.getOrDefault("fullName", personal.get("name"));
        if (name != null) {
            m.put("applicant_name", name);
        }
        Object pan = personal.getOrDefault("pan", personal.get("panNumber"));
        if (pan != null) {
            m.put("pan", pan);
        }
        Object gstin = business.getOrDefault("gstin", business.get("gstNumber"));
        if (gstin != null) {
            m.put("gstin", gstin);
        }
        Object cin = business.get("cin");
        if (cin != null) {
            m.put("cin", cin);
        }
        return m;
    }

    private Map<String, Object> applicationHints(LoanApplication app, Map<String, Object> fields) {
        Map<String, Object> h = new LinkedHashMap<>();
        if (fields.get("pan") != null) {
            h.put("panPresent", true);
        }
        if (fields.get("gstin") != null) {
            h.put("gstinPresent", true);
        }
        if (fields.get("cin") != null) {
            h.put("cinPresent", true);
        }
        return h;
    }

    private Map<String, Object> workflowProvenance(LoanApplication app) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", false);
        ActiveWorkflowConfigService wf = workflowConfigService.getIfAvailable();
        if (wf != null) {
            try {
                wf.findActiveForApplication(app).ifPresent(cfg -> {
                    m.put("available", true);
                    m.put("workflowConfigId", cfg.getId());
                    m.put("workflowConfigVersion", cfg.getVersion());
                    m.put("productCode", cfg.getLoanProduct());
                });
            } catch (Exception ex) {
                m.put("error", ex.getClass().getSimpleName());
            }
        }
        if (!Boolean.TRUE.equals(m.get("available"))) {
            m.put("note", "Workflow provenance incomplete — do not claim full cross-system replay");
        }
        return m;
    }

    private boolean isKycShadowEnabled() {
        if (properties.getShadowEvaluation() != null && properties.getShadowEvaluation().isKycShadowEnabled()) {
            return true;
        }
        return properties.getStagingDemo() != null && properties.getStagingDemo().isEnabled();
    }

    private UUID tenantId() {
        return properties.getDefaultTenantId();
    }
}
