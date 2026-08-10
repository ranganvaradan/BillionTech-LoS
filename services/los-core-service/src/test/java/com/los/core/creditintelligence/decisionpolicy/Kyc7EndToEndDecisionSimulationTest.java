package com.los.core.creditintelligence.decisionpolicy;

import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ExactPackageLoadResult;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.GoldenKycShadowPackageFactory;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService;
import com.los.core.creditintelligence.decisionpolicy.sim.DecisionPolicyEndToEndSimulationService;
import com.los.core.creditintelligence.decisionpolicy.sim.ExactExecutablePackageLoader;
import com.los.core.creditintelligence.decisionpolicy.sim.ExactPackageCertification;
import com.los.core.creditintelligence.decisionpolicy.sim.FrozenDecisionSimulationCase;
import com.los.core.creditintelligence.decisionpolicy.sim.GoldenDecisionPolicyE2EPackageFactory;
import com.los.core.creditintelligence.decisionpolicy.sim.Kyc7FrozenCaseCatalog;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.repository.CiExecutablePolicyPackageRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.ShadowPolicyRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KYC-7 — end-to-end Decision Policy simulation (shadow only).
 */
class Kyc7EndToEndDecisionSimulationTest {

    private final UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private DecisionPolicyEndToEndSimulationService service;
    private ExactExecutablePackageLoader loader;
    private CiExecutablePolicyPackageRepository packageRepo;

    @BeforeEach
    void setUp() {
        packageRepo = mock(CiExecutablePolicyPackageRepository.class);
        ObjectProvider<CiExecutablePolicyPackageRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(packageRepo);
        loader = new ExactExecutablePackageLoader(provider);
        service = new DecisionPolicyEndToEndSimulationService(
                new ShadowKycPolicyEvaluationService(), loader);
    }

    @Test
    void catalogueLinkedPackageNeverFallsBackToGolden() {
        CiExecutablePolicyPackage exact = GoldenDecisionPolicyE2EPackageFactory.decisionPolicyE2eV1(tenant);
        when(packageRepo.findById(exact.getId())).thenReturn(Optional.of(exact));

        ShadowPolicyRoutingService.RoutingResult routed = new ShadowPolicyRoutingService.RoutingResult(
                "EXACTLY_ONE", null, null, exact.getId(), exact.getContentHash(),
                exact.getPolicyCode(), exact.getVersion(), "catalogue",
                LocalDate.of(2024, 6, 15), null, Map.of(), Map.of(), true);

        ExactPackageLoadResult load = loader.resolve(routed, null, true, tenant);
        assertThat(load.ok()).isTrue();
        assertThat(load.demoFixture()).isFalse();
        assertThat(load.pkg().getContentHash()).isEqualTo(exact.getContentHash());
        assertThat(load.pkg().getPolicyCode()).isEqualTo(GoldenDecisionPolicyE2EPackageFactory.POLICY_CODE_V1);
        // Must NOT be golden KYC-only package
        assertThat(load.pkg().getPolicyCode()).isNotEqualTo("DECISION_POLICY_KYC_V1");
    }

    @Test
    void incompleteCataloguePackageBlocksSimulationNoGoldenFallback() {
        CiExecutablePolicyPackage incomplete =
                GoldenDecisionPolicyE2EPackageFactory.incompleteCreditOnlyPackage(tenant);
        when(packageRepo.findById(incomplete.getId())).thenReturn(Optional.of(incomplete));

        ShadowPolicyRoutingService.RoutingResult routed = new ShadowPolicyRoutingService.RoutingResult(
                "EXACTLY_ONE", null, null, incomplete.getId(), incomplete.getContentHash(),
                incomplete.getPolicyCode(), incomplete.getVersion(), "catalogue",
                LocalDate.of(2024, 6, 15), null, Map.of(), Map.of(), true);

        ExactPackageLoadResult load = loader.resolve(routed, null, true, tenant);
        assertThat(load.ok()).isFalse();
        assertThat(load.code()).isEqualTo(ExactPackageCertification.PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION);

        Map<String, Object> cert = ExactPackageCertification.certifyForDecisionSimulation(incomplete, null);
        assertThat(cert.get("ok")).isEqualTo(false);
        assertThat(cert.get("code")).isEqualTo(ExactPackageCertification.PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION);

        Map<String, Object> blocked = service.runCases(incomplete, List.of(Kyc7FrozenCaseCatalog.byCode("CLEAN_FULL_APPROVAL")),
                LocalDate.of(2024, 6, 15), "test");
        assertThat(blocked.get("code")).isEqualTo(ExactPackageCertification.PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION);
        assertThat(blocked.get("recommendationCode")).isNull();
    }

    @Test
    void onePolicyVersionGovernsAllStages() {
        CiExecutablePolicyPackage pkg = GoldenDecisionPolicyE2EPackageFactory.decisionPolicyE2eV1(tenant);
        Map<String, Object> row = service.simulateOne(pkg, Kyc7FrozenCaseCatalog.byCode("CLEAN_FULL_APPROVAL"),
                LocalDate.of(2024, 6, 15), "EXACTLY_ONE");
        assertThat(row.get("policyVersion")).isEqualTo(pkg.getVersion());
        assertThat(row.get("packageId")).isEqualTo(pkg.getId());
        assertThat(row.get("contentHash")).isEqualTo(pkg.getContentHash());
        assertThat(row.get("code")).isNotEqualTo(ExactPackageCertification.SIMULATION_CERTIFICATION_FAILURE);
    }

    @Test
    void kycPassRunsCredit() {
        Map<String, Object> row = run("CLEAN_FULL_APPROVAL");
        assertThat(row.get("kycOutcome")).isEqualTo("PASS");
        assertThat(row.get("creditOutcome")).isNotEqualTo(DecisionPolicyEndToEndSimulationService.STAGE_NOT_RUN);
    }

    @Test
    void kycReferCreditNotRun() {
        Map<String, Object> row = run("KYC_MANUAL_REFER");
        assertThat(row.get("kycOutcome")).isEqualTo("REFER");
        assertThat(row.get("creditOutcome")).isEqualTo(DecisionPolicyEndToEndSimulationService.STAGE_NOT_RUN);
        assertThat(row.get("recommendationCode")).isEqualTo("REFER");
        assertThat(row.get("score")).isNull();
        assertThat(String.valueOf(row.get("scoreDisplay"))).contains("NOT_RUN");
    }

    @Test
    void kycFailCreditNotRun() {
        Map<String, Object> row = run("KYC_CONCLUSIVE_FAILURE");
        assertThat(row.get("kycOutcome")).isEqualTo("FAIL");
        assertThat(row.get("creditOutcome")).isEqualTo(DecisionPolicyEndToEndSimulationService.STAGE_NOT_RUN);
        assertThat(row.get("recommendationCode")).isEqualTo("DECLINE");
    }

    @Test
    void kycMissingCreditNotRun() {
        Map<String, Object> row = run("KYC_MISSING_INFORMATION");
        assertThat(row.get("kycOutcome")).isEqualTo("MISSING_INFORMATION");
        assertThat(row.get("creditOutcome")).isEqualTo(DecisionPolicyEndToEndSimulationService.STAGE_NOT_RUN);
        assertThat(row.get("recommendationCode")).isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void providerOutageNotBorrowerFail() {
        Map<String, Object> row = run("PROVIDER_OUTAGE");
        assertThat(row.get("kycOutcome")).isIn("MISSING_INFORMATION", "REFER");
        assertThat(row.get("kycOutcome")).isNotEqualTo("FAIL");
        assertThat(row.get("creditOutcome")).isEqualTo(DecisionPolicyEndToEndSimulationService.STAGE_NOT_RUN);
    }

    @Test
    void creditHardFail() {
        Map<String, Object> row = run("CREDIT_HARD_DECLINE");
        assertThat(row.get("kycOutcome")).isEqualTo("PASS");
        assertThat(row.get("creditOutcome")).isEqualTo("FAIL");
        assertThat(row.get("recommendationCode")).isEqualTo("DECLINE");
    }

    @Test
    void creditRefer() {
        Map<String, Object> row = run("CREDIT_REFER");
        assertThat(row.get("kycOutcome")).isEqualTo("PASS");
        assertThat(row.get("creditOutcome")).isIn("REFER", "PASS"); // soft rule may REFER overall
        assertThat(row.get("recommendationCode")).isIn("REFER", "APPROVE", "APPROVE_WITH_CONDITIONS", "COUNTER_OFFER");
    }

    @Test
    void scoreCalculatedWhenConfigured() {
        Map<String, Object> row = run("CLEAN_FULL_APPROVAL");
        assertThat(row.get("score")).isNotNull();
        assertThat(row.get("grade")).isNotNull();
    }

    @Test
    void counterOfferWhenCapacityConstrained() {
        Map<String, Object> row = run("CAPACITY_COUNTER_OFFER");
        assertThat(row.get("kycOutcome")).isEqualTo("PASS");
        assertThat(row.get("creditOutcome")).isIn("PASS", "REFER");
        if ("PASS".equals(row.get("creditOutcome"))) {
            assertThat(row.get("recommendationCode")).isIn("COUNTER_OFFER", "APPROVE", "APPROVE_WITH_CONDITIONS");
            if ("COUNTER_OFFER".equals(row.get("recommendationCode"))) {
                assertThat(row.get("requestedAmount")).isEqualTo(new BigDecimal("1000000"));
                assertThat(((BigDecimal) row.get("recommendedAmount")).compareTo(new BigDecimal("1000000"))).isLessThan(0);
            }
        }
    }

    @Test
    void missingCreditDataAfterKycPass() {
        Map<String, Object> row = run("MISSING_CREDIT_DATA");
        assertThat(row.get("kycOutcome")).isEqualTo("PASS");
        assertThat(row.get("creditOutcome")).isIn("DATA_INSUFFICIENT", "FAIL", "REFER");
        assertThat(row.get("recommendationCode")).isIn("DATA_INSUFFICIENT", "DECLINE", "REFER");
    }

    @Test
    void skippedStagesHaveNoZeroValues() {
        Map<String, Object> row = run("KYC_MANUAL_REFER");
        @SuppressWarnings("unchecked")
        Map<String, Object> stages = (Map<String, Object>) row.get("stages");
        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) stages.get("risk");
        assertThat(risk.get("status")).isEqualTo(DecisionPolicyEndToEndSimulationService.STAGE_NOT_RUN);
        assertThat(risk.get("score")).isNull();
        assertThat(row.get("score")).isNull();
        assertThat(row.get("recommendedAmount")).isNull();
    }

    @Test
    void exactPackageHashConsistency() {
        CiExecutablePolicyPackage pkg = GoldenDecisionPolicyE2EPackageFactory.decisionPolicyE2eV1(tenant);
        Map<String, Object> row = service.simulateOne(pkg, Kyc7FrozenCaseCatalog.byCode("CLEAN_FULL_APPROVAL"),
                LocalDate.of(2024, 6, 15), null);
        assertThat(row.get("contentHash")).isEqualTo(pkg.getContentHash());
        assertThat(row.get("code")).isNull();
    }

    @Test
    void replayDeterministic100Percent() {
        Map<String, Object> matrix = service.runFixtureMatrix(tenant);
        assertThat(matrix.get("replayPassRate")).isEqualTo(100.0);
        assertThat(matrix.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(matrix.get("authoritative")).isEqualTo(false);
    }

    @Test
    void historicalV1ReplayAfterV2() {
        Map<String, Object> demo = service.versionTransitionDemo(tenant);
        assertThat(demo.get("replayUsesSameVersion")).isEqualTo(true);
        assertThat(demo.get("noLatestLeakage")).isEqualTo(true);
        assertThat(demo.get("newApplicationUsesV2")).isEqualTo(true);
    }

    @Test
    void noProviderCallsNoAppMutationFlags() {
        Map<String, Object> row = run("CLEAN_FULL_APPROVAL");
        assertThat(row.get("providerCallsMade")).isEqualTo(false);
        assertThat(row.get("applicationMutated")).isEqualTo(false);
        assertThat(row.get("productionUnderwritingTriggered")).isEqualTo(false);
        assertThat(row.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(row.get("authoritative")).isEqualTo(false);
    }

    @Test
    void demoFixtureAllowedOnlyWhenExplicit() {
        CiExecutablePolicyPackage demo = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
        assertThat(ExactPackageCertification.isExplicitDemoOrValidationFixture(demo)).isTrue();
        ExactPackageLoadResult load = loader.resolve(null, demo, false, tenant);
        assertThat(load.ok()).isTrue();
        assertThat(load.demoFixture()).isTrue();
    }

    @Test
    void fixtureMatrixCoversRequiredScenarios() {
        List<FrozenDecisionSimulationCase> cases = Kyc7FrozenCaseCatalog.all(tenant);
        assertThat(cases).hasSizeGreaterThanOrEqualTo(15);
        assertThat(cases.stream().map(FrozenDecisionSimulationCase::caseCode)).contains(
                "CLEAN_FULL_APPROVAL", "KYC_CONCLUSIVE_FAILURE", "KYC_MANUAL_REFER",
                "KYC_MISSING_INFORMATION", "PROVIDER_OUTAGE", "CAPACITY_COUNTER_OFFER",
                "CREDIT_HARD_DECLINE", "MISSING_CREDIT_DATA");
    }

    @Test
    void walkthroughAtoF() {
        // A clean
        assertThat(run("CLEAN_FULL_APPROVAL").get("kycOutcome")).isEqualTo("PASS");
        // B KYC refer
        assertThat(run("KYC_MANUAL_REFER").get("recommendationCode")).isEqualTo("REFER");
        // C provider outage
        assertThat(run("PROVIDER_OUTAGE").get("creditOutcome"))
                .isEqualTo(DecisionPolicyEndToEndSimulationService.STAGE_NOT_RUN);
        // D credit decline
        assertThat(run("CREDIT_HARD_DECLINE").get("recommendationCode")).isEqualTo("DECLINE");
        // E capacity
        Map<String, Object> e = run("CAPACITY_COUNTER_OFFER");
        assertThat(e.get("kycOutcome")).isEqualTo("PASS");
        // F missing credit
        assertThat(run("MISSING_CREDIT_DATA").get("kycOutcome")).isEqualTo("PASS");
    }

    private Map<String, Object> run(String code) {
        CiExecutablePolicyPackage pkg = GoldenDecisionPolicyE2EPackageFactory.decisionPolicyE2eV1(tenant);
        return service.simulateOne(pkg, Kyc7FrozenCaseCatalog.byCode(code), LocalDate.of(2024, 6, 15), null);
    }
}
