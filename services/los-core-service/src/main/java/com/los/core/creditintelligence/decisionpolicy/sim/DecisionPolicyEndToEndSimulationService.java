package com.los.core.creditintelligence.decisionpolicy.sim;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.domain.RecommendationOutcome;
import com.los.core.creditintelligence.decision.domain.RecommendationStatus;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;
import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ExactPackageLoadResult;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycEvaluationRequest;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.service.ShadowPolicyEngine;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * KYC-7 — End-to-end Decision Policy simulation orchestrator (shadow only).
 * KYC → (gate) → Credit → Risk/Score → Limit/Pricing → Recommendation.
 * Reuses existing engines; never mutates applications or enables canonical authority.
 */
@Service
public class DecisionPolicyEndToEndSimulationService {

    public static final String BANNER =
            "SIMULATION ONLY — DOES NOT AFFECT PRODUCTION UNDERWRITING";
    public static final String LABEL = "DECISION_POLICY_E2E_SIMULATION";
    public static final String STAGE_NOT_RUN = "NOT_RUN";

    private final ShadowKycPolicyEvaluationService kycService;
    private final ShadowPolicyEngine policyEngine;
    private final ShadowDecisionEngine decisionEngine;
    private final ExactExecutablePackageLoader packageLoader;
    private final ContentHasher hasher = new ContentHasher();

    public DecisionPolicyEndToEndSimulationService(
            ShadowKycPolicyEvaluationService kycService,
            ExactExecutablePackageLoader packageLoader) {
        this.kycService = kycService;
        this.packageLoader = packageLoader;
        this.policyEngine = new ShadowPolicyEngine();
        this.decisionEngine = new ShadowDecisionEngine();
    }

    /** Run full fixture matrix (VALIDATION FIXTURE cases). */
    public Map<String, Object> runFixtureMatrix(UUID tenantId) {
        CiExecutablePolicyPackage pkg = GoldenDecisionPolicyE2EPackageFactory.decisionPolicyE2eV1(tenantId);
        List<FrozenDecisionSimulationCase> cases = Kyc7FrozenCaseCatalog.all(tenantId);
        return runCases(pkg, cases, LocalDate.of(2024, 6, 15), null);
    }

    public Map<String, Object> runCases(
            CiExecutablePolicyPackage pkg,
            List<FrozenDecisionSimulationCase> cases,
            LocalDate evaluationBusinessDate,
            String applicabilityReason
    ) {
        Map<String, Object> cert = ExactPackageCertification.certifyForDecisionSimulation(pkg, null);
        if (!Boolean.TRUE.equals(cert.get("ok"))) {
            Map<String, Object> blocked = new LinkedHashMap<>(cert);
            blocked.put("status", "BLOCKED");
            blocked.put("simulationBanner", BANNER);
            blocked.put("authoritative", false);
            blocked.put("allowCanonicalAuthority", false);
            return blocked;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> kycCounts = zeroCounts("PASS", "REFER", "FAIL", "MISSING_INFORMATION");
        Map<String, Integer> creditCounts = zeroCounts("PASS", "REFER", "FAIL", "DATA_INSUFFICIENT", "NOT_RUN");
        Map<String, Integer> recCounts = zeroCounts(
                "APPROVE", "APPROVE_WITH_CONDITIONS", "COUNTER_OFFER", "REFER", "DECLINE", "DATA_INSUFFICIENT");
        Map<String, Integer> kycBlockers = new LinkedHashMap<>();
        Map<String, Integer> creditBlockers = new LinkedHashMap<>();
        Map<String, Integer> counterReasons = new LinkedHashMap<>();
        Map<String, Integer> missingFamilies = new LinkedHashMap<>();

        int replayMatch = 0;
        for (FrozenDecisionSimulationCase c : cases) {
            Map<String, Object> first = simulateOne(pkg, c, evaluationBusinessDate, applicabilityReason);
            Map<String, Object> second = simulateOne(pkg, c, evaluationBusinessDate, applicabilityReason);
            boolean replayOk = deterministicEqual(first, second);
            first.put("replayMatch", replayOk);
            if (replayOk) {
                replayMatch++;
            }
            rows.add(first);

            String kyc = String.valueOf(first.get("kycOutcome"));
            kycCounts.merge(normalizeKey(kyc), 1, Integer::sum);
            String credit = String.valueOf(first.get("creditOutcome"));
            creditCounts.merge(normalizeKey(credit), 1, Integer::sum);
            String rec = String.valueOf(first.get("recommendationCode"));
            recCounts.merge(normalizeKey(rec), 1, Integer::sum);

            tallyList(kycBlockers, first.get("kycBlockers"));
            tallyList(creditBlockers, first.get("creditBlockers"));
            if ("COUNTER_OFFER".equals(rec)) {
                Object reason = first.get("counterOfferReason");
                if (reason != null) {
                    counterReasons.merge(String.valueOf(reason), 1, Integer::sum);
                }
            }
            tallyList(missingFamilies, first.get("missingDataFamilies"));
        }

        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put("applicationsTested", rows.size());
        aggregates.put("kyc", kycCounts);
        aggregates.put("credit", creditCounts);
        aggregates.put("recommendations", recCounts);
        aggregates.put("approve", recCounts.getOrDefault("APPROVE", 0));
        aggregates.put("approveWithConditions", recCounts.getOrDefault("APPROVE_WITH_CONDITIONS", 0));
        aggregates.put("counterOffer", recCounts.getOrDefault("COUNTER_OFFER", 0));
        aggregates.put("recommendRefer", recCounts.getOrDefault("REFER", 0));
        aggregates.put("decline", recCounts.getOrDefault("DECLINE", 0));
        aggregates.put("dataInsufficient", recCounts.getOrDefault("DATA_INSUFFICIENT", 0));
        aggregates.put("passed", creditCounts.getOrDefault("PASS", 0));
        aggregates.put("referred", creditCounts.getOrDefault("REFER", 0)
                + kycCounts.getOrDefault("REFER", 0));
        aggregates.put("failed", creditCounts.getOrDefault("FAIL", 0));

        Map<String, Object> out = new LinkedHashMap<>();
        stampSafety(out);
        out.put("simulationBanner", BANNER);
        out.put("label", LABEL);
        out.put("certificationStatus", "SIMULATION_VALIDATED");
        out.put("certificationNote",
                "Fixtures are sufficient for technical simulation validation only — "
                        + "NOT for production cutover or real-data certification.");
        out.put("policyName", pkg.getPolicyCode());
        out.put("policyVersion", pkg.getVersion());
        out.put("policyStatus", pkg.getStatus());
        out.put("packageId", pkg.getId());
        out.put("contentHash", pkg.getContentHash());
        out.put("evaluationBusinessDate", evaluationBusinessDate == null ? null : evaluationBusinessDate.toString());
        out.put("applicabilityReason", applicabilityReason == null
                ? "VALIDATION FIXTURE — explicit demo Decision Policy package" : applicabilityReason);
        out.put("products", List.of("TERM_LOAN", "SCF_DEMO"));
        out.put("aggregates", aggregates);
        out.put("topKycBlockers", topN(kycBlockers, 8));
        out.put("topCreditBlockers", topN(creditBlockers, 8));
        out.put("topCounterOfferReasons", topN(counterReasons, 8));
        out.put("topMissingDataFamilies", topN(missingFamilies, 8));
        out.put("applications", rows);
        out.put("replayPassRate", rows.isEmpty() ? 0.0
                : (100.0 * replayMatch / rows.size()));
        out.put("replayMatched", replayMatch);
        out.put("replayTotal", rows.size());
        out.put("fixtureBanner", GoldenDecisionPolicyE2EPackageFactory.DEMO_LABEL);
        out.put("technicalDetails", Map.of(
                "packageId", pkg.getId(),
                "contentHash", pkg.getContentHash(),
                "policyCode", pkg.getPolicyCode(),
                "policyVersion", pkg.getVersion(),
                "dslVersion", pkg.getDslVersion(),
                "allowCanonicalAuthority", false,
                "collapsed", true));
        return out;
    }

    public Map<String, Object> simulateOne(
            CiExecutablePolicyPackage pkg,
            FrozenDecisionSimulationCase c,
            LocalDate evaluationBusinessDate,
            String applicabilityReason
    ) {
        Map<String, Object> cert = ExactPackageCertification.certifyForDecisionSimulation(
                pkg, new ExactPackageCertification.UUIDExpectation(pkg.getId(), pkg.getVersion(), pkg.getContentHash()));
        if (!Boolean.TRUE.equals(cert.get("ok"))) {
            Map<String, Object> fail = new LinkedHashMap<>();
            stampSafety(fail);
            fail.put("caseCode", c.caseCode());
            fail.put("status", "BLOCKED");
            fail.put("code", cert.get("code"));
            fail.put("reason", cert.get("reason"));
            fail.put("recommendationCode", null);
            fail.put("simulationBanner", BANNER);
            return fail;
        }

        FixedEvaluationClock clock = FixedEvaluationClock.atLocalNoon(
                evaluationBusinessDate != null ? evaluationBusinessDate : LocalDate.of(2024, 6, 15),
                ZoneId.of("Asia/Kolkata"));

        // --- Stage 1: KYC ---
        Map<String, Object> kyc = kycService.evaluate(ShadowKycEvaluationRequest.builder()
                .tenantId(pkg.getTenantId())
                .applicationId(c.applicationId())
                .policyPackage(pkg)
                .routingOutcome("EXACTLY_ONE")
                .routingReason(applicabilityReason)
                .stepEvidence(c.kycSteps())
                .applicationFields(c.applicationFields())
                .applicationHints(c.applicationHints())
                .productionKycOutcome(c.legacyKycOutcome())
                .evaluationBusinessDate(evaluationBusinessDate)
                .workflowProvenance(Map.of("simulation", true, "providerCallsMade", false))
                .evidenceRefs(List.of())
                .persist(false)
                .build());

        String kycOutcome = String.valueOf(kyc.get("overallOutcome"));
        boolean kycPass = KycBusinessOutcome.PASS.name().equals(kycOutcome);

        Map<String, Object> stages = new LinkedHashMap<>();
        stages.put("kyc", stageView("KYC & Eligibility", kycOutcome, kyc, false));

        String creditOutcome = STAGE_NOT_RUN;
        String creditNotRunReason = null;
        Map<String, Object> creditDetail = Map.of("status", STAGE_NOT_RUN);
        Map<String, Object> scoreDetail = notRunScore();
        Map<String, Object> offerDetail = notRunOffer();
        String recommendationCode;
        String recommendationLabel;
        boolean humanReview = true;
        List<Object> conditions = List.of();
        String authority = null;
        List<Object> deviations = List.of();
        String counterOfferReason = null;
        BigDecimal recommendedAmount = null;
        Integer recommendedTenure = null;
        BigDecimal recommendedRate = null;
        CiPolicyEvaluation creditEval = null;
        CiCreditRecommendation rec = null;

        if (!kycPass) {
            creditNotRunReason = kycGateReason(kycOutcome);
            stages.put("credit", notRunStage("Credit Assessment", creditNotRunReason));
            stages.put("risk", notRunStage("Risk / Score", creditNotRunReason));
            stages.put("offer", notRunStage("Limit & Pricing", creditNotRunReason));
            recommendationCode = recommendationFromKycGate(kycOutcome);
            recommendationLabel = labelFor(recommendationCode);
            stages.put("decision", Map.of(
                    "name", "Decision",
                    "status", recommendationCode,
                    "label", recommendationLabel,
                    "fromKycGate", true,
                    "reason", creditNotRunReason));
        } else {
            // --- Stage 2+3: Credit + Score (same package, credit-only rule body) ---
            CiExecutablePolicyPackage creditView = creditOnlyView(pkg);
            PolicyEvaluationInput policyInput = new PolicyEvaluationInput(
                    pkg.getTenantId(),
                    c.evaluationContextId(),
                    mergeFacts(kyc, c.facts()),
                    c.metrics(),
                    c.reconciliations(),
                    mergeParams(pkg, c.policyParameters()),
                    c.applicationFields(),
                    clock,
                    Map.of(
                            "simulationOnly", true,
                            "validationFixture", true,
                            "caseCode", c.caseCode(),
                            "sourcePackageId", pkg.getId(),
                            "sourceContentHash", pkg.getContentHash()));

            creditEval = policyEngine.evaluate(creditView, policyInput);
            creditOutcome = creditEval.getOverallOutcome() == null
                    ? "DATA_INSUFFICIENT" : creditEval.getOverallOutcome();
            creditDetail = creditStageDetail(creditEval);
            stages.put("credit", stageView("Credit Assessment", creditOutcome, creditDetail, false));

            scoreDetail = scoreStageDetail(creditEval, pkg);
            stages.put("risk", scoreDetail);

            // --- Stage 4+5: Limit / Pricing / Decision ---
            CiDecisionStrategy strategy = strategyFromPackage(pkg);
            DecisionRuntimeInput din = DecisionRuntimeInput.builder()
                    .tenantId(pkg.getTenantId())
                    .applicationId(c.applicationId())
                    .evaluationContextId(c.evaluationContextId())
                    .policyEvaluationId(creditEval.getId())
                    .policyPackageId(pkg.getId())
                    .productCode(c.productCode())
                    .policyOverallOutcome(normalizePolicyOutcome(creditOutcome))
                    .scoreResult(sanitizeMap(scoreDetail.get("scoreResult") instanceof Map<?, ?> sr
                            ? castMap(sr) : Map.of()))
                    .requestedAmount(c.requestedAmount())
                    .requestedTenureMonths(c.requestedTenureMonths())
                    .facts(sanitizeMap(mergeFacts(kyc, c.facts())))
                    .metrics(sanitizeMap(c.metrics()))
                    .reconciliations(sanitizeMap(c.reconciliations()))
                    .policyParameters(sanitizeMap(mergeParams(pkg, c.policyParameters())))
                    .applicationFields(sanitizeMap(c.applicationFields()))
                    .clock(clock)
                    .metadata(Map.of(
                            "simulationOnly", true,
                            "validationFixture", true,
                            "caseCode", c.caseCode(),
                            "allowCanonicalAuthority", false))
                    .build();
            rec = decisionEngine.recommend(strategy, din);
            recommendationCode = rec.getRecommendationOutcome();
            recommendationLabel = labelFor(recommendationCode);
            recommendedAmount = rec.getRecommendedAmount();
            recommendedTenure = rec.getRecommendedTenureMonths();
            recommendedRate = rec.getRecommendedFinalRate();
            conditions = rec.getConditionsPrecedent() == null ? List.of() : new ArrayList<>(rec.getConditionsPrecedent());
            if (rec.getConditionsSubsequent() != null) {
                conditions = new ArrayList<>(conditions);
                conditions.addAll(rec.getConditionsSubsequent());
            }
            authority = rec.getApprovalAuthorityLevel();
            humanReview = Boolean.TRUE.equals(rec.getHumanReviewRequired());
            deviations = extractDeviations(rec);
            offerDetail = offerStageDetail(c, rec);
            stages.put("offer", offerDetail);
            stages.put("decision", Map.of(
                    "name", "Decision",
                    "status", recommendationCode,
                    "label", recommendationLabel,
                    "authoritative", false,
                    "statusCode", RecommendationStatus.RECOMMENDED.name(),
                    "humanReviewRequired", humanReview,
                    "authority", authority,
                    "conditionsCount", conditions.size(),
                    "deviations", deviations));

            if (RecommendationOutcome.COUNTER_OFFER.name().equals(recommendationCode)) {
                counterOfferReason = "Eligible amount/tenure below requested — capacity or policy cap constrained";
            }
        }

        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("policyContentHash", pkg.getContentHash());
        hashPayload.put("policyVersion", pkg.getVersion());
        hashPayload.put("packageId", pkg.getId());
        hashPayload.put("kycOutcome", kycOutcome);
        hashPayload.put("creditOutcome", creditOutcome);
        hashPayload.put("score", scoreDetail.get("score"));
        hashPayload.put("grade", scoreDetail.get("grade"));
        hashPayload.put("recommendedAmount", recommendedAmount);
        hashPayload.put("recommendedTenure", recommendedTenure);
        hashPayload.put("recommendedRate", recommendedRate);
        hashPayload.put("recommendation", recommendationCode);
        String decisionHash = hasher.hashMap(hashPayload);

        // Exact package/version/hash consistency
        boolean certified = pkg.getContentHash() != null
                && pkg.getContentHash().equals(String.valueOf(kyc.get("policyContentHash")))
                && pkg.getVersion().equals(String.valueOf(kyc.get("policyVersion")));
        if (creditEval != null) {
            certified = certified && pkg.getId().equals(creditEval.getPolicyPackageId());
        }
        if (!certified) {
            Map<String, Object> fail = new LinkedHashMap<>();
            stampSafety(fail);
            fail.put("caseCode", c.caseCode());
            fail.put("code", ExactPackageCertification.SIMULATION_CERTIFICATION_FAILURE);
            fail.put("status", "BLOCKED");
            fail.put("recommendationCode", null);
            fail.put("reason", "Resolved policy version / package / hash inconsistency");
            fail.put("simulationBanner", BANNER);
            return fail;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        stampSafety(row);
        row.put("caseCode", c.caseCode());
        row.put("applicationCode", c.caseCode());
        row.put("displayName", c.displayName());
        row.put("scenarioLabel", c.scenarioLabel());
        row.put("label", "VALIDATION FIXTURE");
        row.put("product", c.productCode());
        row.put("requestedAmount", c.requestedAmount());
        row.put("requestedAmountDisplay", formatInr(c.requestedAmount()));
        row.put("requestedTenureMonths", c.requestedTenureMonths());
        row.put("kycOutcome", kycOutcome);
        row.put("kyc", kycOutcome);
        row.put("creditOutcome", creditOutcome);
        row.put("credit", creditOutcome.equals(STAGE_NOT_RUN) ? "CREDIT NOT RUN" : creditOutcome);
        row.put("creditNotRunReason", creditNotRunReason);
        row.put("score", scoreDetail.get("score"));
        row.put("grade", scoreDetail.get("grade"));
        row.put("scoreDisplay", scoreDisplay(scoreDetail));
        row.put("recommendedAmount", recommendedAmount);
        row.put("recommendedAmountDisplay", recommendedAmount == null ? null : formatInr(recommendedAmount));
        row.put("recommendedTenureMonths", recommendedTenure);
        row.put("recommendedRate", recommendedRate);
        row.put("recommendationCode", recommendationCode);
        row.put("recommendation", recommendationLabel);
        row.put("decision", recommendationLabel);
        row.put("reviewNeeded", humanReview || needsReview(recommendationCode));
        row.put("reviewRequired", humanReview || needsReview(recommendationCode));
        row.put("conditionsCount", conditions.size());
        row.put("authority", authority);
        row.put("counterOfferReason", counterOfferReason);
        row.put("kycBlockers", blockersFromKyc(kyc));
        row.put("creditBlockers", blockersFromCredit(creditDetail));
        row.put("missingDataFamilies", missingFamilies(kyc, creditDetail));
        row.put("policyCode", pkg.getPolicyCode());
        row.put("policyVersion", pkg.getVersion());
        row.put("packageId", pkg.getId());
        row.put("contentHash", pkg.getContentHash());
        row.put("deterministicDecisionHash", decisionHash);
        row.put("kycDeterministicHash", kyc.get("deterministicHash"));
        row.put("stages", stages);
        row.put("providerCallsMade", false);
        row.put("applicationMutated", false);
        row.put("productionUnderwritingTriggered", false);
        row.put("legacyComparison", legacyComparison(c, kyc, creditOutcome, recommendationCode));
        row.put("drillDown", drillDown(c, kyc, stages, creditDetail, scoreDetail, offerDetail,
                recommendationCode, conditions, authority, deviations, pkg, decisionHash));
        row.put("reasonGraph", reasonGraph(kyc, creditDetail, scoreDetail, offerDetail, recommendationCode, pkg));
        return row;
    }

    public Map<String, Object> versionTransitionDemo(UUID tenantId) {
        LocalDate dateA = LocalDate.of(2024, 6, 15);
        CiExecutablePolicyPackage v1 = GoldenDecisionPolicyE2EPackageFactory.decisionPolicyE2eV1(tenantId);
        CiExecutablePolicyPackage v2 = GoldenDecisionPolicyE2EPackageFactory.decisionPolicyE2eV2(tenantId);
        FrozenDecisionSimulationCase clean = Kyc7FrozenCaseCatalog.byCode("CLEAN_FULL_APPROVAL");

        Map<String, Object> hist = simulateOne(v1, clean, dateA, "Historical pin v1");
        Map<String, Object> replay = simulateOne(v1, clean, dateA, "Historical pin v1");
        Map<String, Object> underV2 = simulateOne(v2, clean, LocalDate.of(2025, 1, 15), "New application under v2");

        Map<String, Object> out = new LinkedHashMap<>();
        stampSafety(out);
        out.put("simulationBanner", BANNER);
        out.put("historicalDate", dateA.toString());
        out.put("historicalPolicy", v1.getPolicyCode());
        out.put("historicalVersion", v1.getVersion());
        out.put("historicalResult", hist.get("recommendationCode"));
        out.put("historicalHash", hist.get("contentHash"));
        out.put("replayUsesSameVersion", v1.getVersion().equals(replay.get("policyVersion")));
        out.put("replayRecommendationMatch",
                String.valueOf(hist.get("recommendationCode")).equals(String.valueOf(replay.get("recommendationCode"))));
        out.put("noLatestLeakage", !v2.getVersion().equals(hist.get("policyVersion")));
        out.put("newApplicationPolicy", underV2.get("policyVersion"));
        out.put("newApplicationUsesV2", "2".equals(String.valueOf(underV2.get("policyVersion"))));
        return out;
    }

    public Map<String, Object> certifyCataloguePackage(CiExecutablePolicyPackage pkg, UUID expectedId, String expectedHash) {
        ExactPackageLoadResult synthetic = pkg == null
                ? ExactPackageLoadResult.notFound(expectedId)
                : ExactPackageLoadResult.ok(pkg, ExactPackageCertification.isExplicitDemoOrValidationFixture(pkg));
        if (pkg != null && expectedId != null && !expectedId.equals(pkg.getId())) {
            return ExactPackageLoadResult.certificationFailure(pkg, "package id mismatch").toMap();
        }
        Map<String, Object> cert = ExactPackageCertification.certifyForDecisionSimulation(
                pkg, new ExactPackageCertification.UUIDExpectation(expectedId, null, expectedHash));
        cert.put("loader", synthetic.toMap());
        return cert;
    }

    // --- helpers ---

    static CiExecutablePolicyPackage creditOnlyView(CiExecutablePolicyPackage pkg) {
        if (pkg == null || pkg.getContent() == null) {
            return pkg;
        }
        Map<String, Object> content = new LinkedHashMap<>(pkg.getContent());
        List<Map<String, Object>> all = new ArrayList<>();
        if (content.get("rules") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rule = (Map<String, Object>) raw;
                    if (!ShadowKycPolicyEvaluationService.isKycEligibilityRule(rule)) {
                        all.add(rule);
                    }
                }
            }
        }
        content.put("rules", all);
        List<Map<String, Object>> stages = new ArrayList<>();
        if (content.get("stageDefinitions") instanceof List<?> sl) {
            for (Object o : sl) {
                if (!(o instanceof Map<?, ?> sm)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> stage = new LinkedHashMap<>((Map<String, Object>) sm);
                String code = String.valueOf(stage.get("stageCode"));
                if (com.los.core.creditintelligence.decisionpolicy.DecisionPolicyStages.isKycEligibilityStage(code)) {
                    continue;
                }
                stages.add(stage);
            }
        }
        content.put("stageDefinitions", stages);
        // Preserve identity: same id/hash/version as source package for certification
        return CiExecutablePolicyPackage.builder()
                .id(pkg.getId())
                .tenantId(pkg.getTenantId())
                .policyCode(pkg.getPolicyCode())
                .version(pkg.getVersion())
                .status(pkg.getStatus())
                .dslVersion(pkg.getDslVersion())
                .evaluationSemanticsVersion(pkg.getEvaluationSemanticsVersion())
                .content(content)
                .contentHash(pkg.getContentHash())
                .createdAt(pkg.getCreatedAt())
                .createdBy(pkg.getCreatedBy())
                .publishedAt(pkg.getPublishedAt())
                .publishedBy(pkg.getPublishedBy())
                .approvalMetadata(pkg.getApprovalMetadata())
                .build();
    }

    private static CiDecisionStrategy strategyFromPackage(CiExecutablePolicyPackage pkg) {
        if (pkg.getContent() != null && pkg.getContent().get("decisionStrategy") instanceof Map<?, ?> ds) {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = new LinkedHashMap<>((Map<String, Object>) ds);
            return CiDecisionStrategy.builder()
                    .id(UUID.nameUUIDFromBytes(("strategy:" + pkg.getId()).getBytes()))
                    .tenantId(pkg.getTenantId())
                    .strategyCode(String.valueOf(content.getOrDefault("strategyCode",
                            DecisionStrategyFactory.STRATEGY_CODE)))
                    .version(pkg.getVersion())
                    .status("SHADOW")
                    .content(content)
                    .contentHash(pkg.getContentHash())
                    .createdAt(Instant.parse("2024-06-15T00:00:00Z"))
                    .createdBy("PACKAGE")
                    .build();
        }
        return DecisionStrategyFactory.p2ValidationStrategyV1(pkg.getTenantId());
    }

    private static String kycGateReason(String kycOutcome) {
        return switch (normalizeKey(kycOutcome)) {
            case "REFER" -> "KYC REVIEW REQUIRED — Credit stage not run";
            case "FAIL" -> "KYC FAILED — Credit stage not run";
            case "MISSING_INFORMATION" -> "MISSING INFORMATION — Credit stage not run";
            default -> "KYC did not PASS — Credit stage not run";
        };
    }

    private static String recommendationFromKycGate(String kycOutcome) {
        return switch (normalizeKey(kycOutcome)) {
            case "REFER" -> RecommendationOutcome.REFER.name();
            case "FAIL" -> RecommendationOutcome.DECLINE.name();
            case "MISSING_INFORMATION" -> RecommendationOutcome.DATA_INSUFFICIENT.name();
            default -> RecommendationOutcome.REFER.name();
        };
    }

    private static Map<String, Object> notRunStage(String name, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("status", STAGE_NOT_RUN);
        m.put("display", "CREDIT NOT RUN".equals(name) || name.startsWith("Credit")
                ? "CREDIT NOT RUN" : STAGE_NOT_RUN);
        m.put("reason", reason);
        m.put("score", null);
        m.put("grade", null);
        m.put("amount", null);
        return m;
    }

    private static Map<String, Object> notRunScore() {
        Map<String, Object> m = notRunStage("Risk / Score", "Prior stage blocked");
        m.put("score", null);
        m.put("grade", null);
        m.put("scoreResult", Map.of());
        return m;
    }

    private static Map<String, Object> notRunOffer() {
        Map<String, Object> m = notRunStage("Limit & Pricing", "Prior stage blocked");
        m.put("requestedAmount", null);
        m.put("recommendedAmount", null);
        return m;
    }

    private static Map<String, Object> stageView(String name, String status, Object detail, boolean skipped) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("status", status);
        m.put("skipped", skipped);
        m.put("detail", detail);
        return m;
    }

    private static Map<String, Object> creditStageDetail(CiPolicyEvaluation eval) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", eval.getOverallOutcome());
        m.put("passCount", eval.getPassCount());
        m.put("failCount", eval.getFailCount());
        m.put("referCount", eval.getReferCount());
        m.put("diCount", eval.getDiCount());
        m.put("ruleCount", eval.getRuleCount());
        m.put("summary", eval.getPassCount() + " passed · "
                + eval.getReferCount() + " referred · "
                + eval.getFailCount() + " failed · "
                + eval.getDiCount() + " DI");
        List<Map<String, Object>> rules = new ArrayList<>();
        if (eval.getRuleResults() != null) {
            eval.getRuleResults().forEach(rr -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("ruleId", rr.getRuleId());
                r.put("outcome", rr.getOutcome());
                r.put("category", rr.getCategory());
                rules.add(r);
            });
        }
        m.put("rules", rules);
        return m;
    }

    private static Map<String, Object> scoreStageDetail(CiPolicyEvaluation eval, CiExecutablePolicyPackage pkg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Risk / Score");
        Map<String, Object> sr = eval.getScoreResult() == null ? Map.of() : eval.getScoreResult();
        boolean configured = pkg.getContent() != null && pkg.getContent().get("scorecard") instanceof Map<?, ?>;
        if (!configured || sr.isEmpty() || "NONE".equals(String.valueOf(sr.get("scorecardCode")))
                || "NOT_APPLICABLE".equals(String.valueOf(sr.get("grade")))) {
            m.put("status", STAGE_NOT_RUN);
            m.put("score", null);
            m.put("grade", null);
            m.put("reason", "No scorecard configured on this Decision Policy package");
            m.put("scoreResult", Map.of());
            return m;
        }
        m.put("status", "RUN");
        m.put("score", sr.get("score"));
        m.put("grade", sr.get("grade"));
        m.put("scorecardCode", sr.get("scorecardCode"));
        m.put("ownership", "POLICY_PACKAGE");
        m.put("scoreResult", sr);
        Object ownership = ((Map<?, ?>) pkg.getContent().get("scorecard")).get("ownership");
        if (ownership == null || !"POLICY_PACKAGE".equals(String.valueOf(ownership))) {
            m.put("label", "LIVE SCORECARD CONFIGURATION — SIMULATION INPUT");
        }
        return m;
    }

    private static Map<String, Object> offerStageDetail(FrozenDecisionSimulationCase c, CiCreditRecommendation rec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Limit & Pricing");
        m.put("status", "RUN");
        m.put("requestedAmount", c.requestedAmount());
        m.put("requestedTenureMonths", c.requestedTenureMonths());
        m.put("recommendedAmount", rec.getRecommendedAmount());
        m.put("recommendedTenureMonths", rec.getRecommendedTenureMonths());
        m.put("recommendedRate", rec.getRecommendedFinalRate());
        m.put("eligibleAmount", rec.getDimensions() == null ? null
                : extractEligible(rec.getDimensions()));
        m.put("collateral", rec.getRecommendedCollateral());
        m.put("dimensions", rec.getDimensions());
        return m;
    }

    private static Object extractEligible(Map<String, Object> dimensions) {
        Object lim = dimensions.get("Limit");
        if (lim instanceof Map<?, ?> lm) {
            return lm.get("eligibleAmount") != null ? lm.get("eligibleAmount") : lm.get("selectedEligibleAmount");
        }
        return null;
    }

    private static List<Object> extractDeviations(CiCreditRecommendation rec) {
        if (rec.getExplanation() == null) {
            return List.of();
        }
        Object d = rec.getExplanation().get("deviations");
        if (d instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    copy.putIfAbsent("status", "REQUESTED");
                    out.add(copy);
                } else {
                    out.add(o);
                }
            }
            return out;
        }
        return List.of();
    }

    private static Map<String, Object> drillDown(
            FrozenDecisionSimulationCase c,
            Map<String, Object> kyc,
            Map<String, Object> stages,
            Map<String, Object> creditDetail,
            Map<String, Object> scoreDetail,
            Map<String, Object> offerDetail,
            String recommendation,
            List<Object> conditions,
            String authority,
            List<Object> deviations,
            CiExecutablePolicyPackage pkg,
            String decisionHash
    ) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("application", Map.of(
                "product", c.productCode(),
                "requestedAmount", c.requestedAmount(),
                "requestedTenureMonths", c.requestedTenureMonths(),
                "scenarioLabel", c.scenarioLabel(),
                "caseCode", c.caseCode(),
                "label", "VALIDATION FIXTURE"));
        d.put("kyc", Map.of(
                "outcome", kyc.get("overallOutcome"),
                "ruleResults", kyc.getOrDefault("ruleResults", List.of()),
                "missingFacts", kyc.getOrDefault("missingFacts", List.of()),
                "referReasons", kyc.getOrDefault("referReasons", List.of()),
                "failReasons", kyc.getOrDefault("failReasons", List.of()),
                "frozenFacts", kyc.getOrDefault("frozenFacts", Map.of())));
        d.put("credit", creditDetail);
        d.put("risk", scoreDetail);
        d.put("offer", offerDetail);
        d.put("decision", Map.of(
                "recommendation", recommendation,
                "conditions", conditions,
                "authority", authority == null ? "" : authority,
                "humanReviewRequired", true,
                "deviations", deviations,
                "authoritative", false,
                "status", RecommendationStatus.RECOMMENDED.name()));
        d.put("technicalDetails", Map.of(
                "packageId", pkg.getId(),
                "contentHash", pkg.getContentHash(),
                "policyVersion", pkg.getVersion(),
                "deterministicDecisionHash", decisionHash,
                "kycHash", kyc.get("deterministicHash"),
                "collapsed", true));
        d.put("stages", stages);
        return d;
    }

    private static Map<String, Object> reasonGraph(
            Map<String, Object> kyc,
            Map<String, Object> credit,
            Map<String, Object> score,
            Map<String, Object> offer,
            String recommendation,
            CiExecutablePolicyPackage pkg
    ) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of("kind", "POLICY", "reference", pkg.getPolicyCode(),
                "version", pkg.getVersion(), "contentHash", pkg.getContentHash()));
        nodes.add(Map.of("kind", "KYC_OUTCOME", "value", String.valueOf(kyc.get("overallOutcome"))));
        if (!STAGE_NOT_RUN.equals(String.valueOf(credit.get("outcome")))) {
            nodes.add(Map.of("kind", "CREDIT_OUTCOME", "value", String.valueOf(credit.get("outcome"))));
        }
        if (score.get("score") != null) {
            nodes.add(Map.of("kind", "RISK_SCORE", "score", score.get("score"), "grade", score.get("grade")));
        }
        if (offer.get("recommendedAmount") != null) {
            nodes.add(Map.of("kind", "OFFER", "recommendedAmount", offer.get("recommendedAmount"),
                    "requestedAmount", offer.get("requestedAmount")));
        }
        nodes.add(Map.of("kind", "RECOMMENDATION", "value", recommendation));
        return Map.of("nodes", nodes, "invented", false);
    }

    private static Map<String, Object> legacyComparison(
            FrozenDecisionSimulationCase c,
            Map<String, Object> kyc,
            String creditOutcome,
            String recommendation
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("analyticalOnly", true);
        m.put("notPartOfDecisionPipeline", true);
        m.put("currentLos", Map.of(
                "kyc", c.legacyKycOutcome() == null ? "N/A" : c.legacyKycOutcome(),
                "credit", c.legacyCreditOutcome() == null ? "N/A" : c.legacyCreditOutcome()));
        m.put("decisionPolicySimulation", Map.of(
                "kyc", kyc.get("overallOutcome"),
                "credit", creditOutcome,
                "recommendation", recommendation));
        m.put("comparisonClass", kyc.getOrDefault("comparisonClass", "POLICY_DIFFERENCE"));
        return m;
    }

    private static List<String> blockersFromKyc(Map<String, Object> kyc) {
        List<String> out = new ArrayList<>();
        addStrings(out, kyc.get("failReasons"));
        addStrings(out, kyc.get("referReasons"));
        addStrings(out, kyc.get("missingFacts"));
        return out;
    }

    private static List<String> blockersFromCredit(Map<String, Object> credit) {
        List<String> out = new ArrayList<>();
        if (credit.get("rules") instanceof List<?> rules) {
            for (Object o : rules) {
                if (o instanceof Map<?, ?> r) {
                    String outcome = String.valueOf(r.get("outcome"));
                    if ("FAIL".equals(outcome) || "REFER".equals(outcome) || "DATA_INSUFFICIENT".equals(outcome)) {
                        out.add(String.valueOf(r.get("ruleId")) + ":" + outcome);
                    }
                }
            }
        }
        return out;
    }

    private static List<String> missingFamilies(Map<String, Object> kyc, Map<String, Object> credit) {
        List<String> out = new ArrayList<>();
        addStrings(out, kyc.get("missingFacts"));
        if (Integer.valueOf(1).equals(credit.get("diCount")) || (credit.get("diCount") instanceof Number n && n.intValue() > 0)) {
            out.add("CREDIT_DATA_INSUFFICIENT");
        }
        return out;
    }

    private static void addStrings(List<String> out, Object raw) {
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
    }

    private static Map<String, Object> mergeFacts(Map<String, Object> kyc, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kyc.get("frozenFacts") instanceof Map<?, ?> f) {
            f.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        if (extra != null) {
            m.putAll(extra);
        }
        return m;
    }

    private static Map<String, Object> mergeParams(CiExecutablePolicyPackage pkg, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (pkg.getContent() != null && pkg.getContent().get("policyParameters") instanceof Map<?, ?> p) {
            p.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        if (extra != null) {
            m.putAll(extra);
        }
        return m;
    }

    private static boolean deterministicEqual(Map<String, Object> a, Map<String, Object> b) {
        return String.valueOf(a.get("deterministicDecisionHash"))
                .equals(String.valueOf(b.get("deterministicDecisionHash")))
                && String.valueOf(a.get("kycOutcome")).equals(String.valueOf(b.get("kycOutcome")))
                && String.valueOf(a.get("creditOutcome")).equals(String.valueOf(b.get("creditOutcome")))
                && String.valueOf(a.get("recommendationCode")).equals(String.valueOf(b.get("recommendationCode")))
                && String.valueOf(a.get("score")).equals(String.valueOf(b.get("score")))
                && String.valueOf(a.get("grade")).equals(String.valueOf(b.get("grade")))
                && String.valueOf(a.get("recommendedAmount")).equals(String.valueOf(b.get("recommendedAmount")))
                && String.valueOf(a.get("contentHash")).equals(String.valueOf(b.get("contentHash")));
    }

    private static void stampSafety(Map<String, Object> m) {
        m.put("shadow", true);
        m.put("authoritative", false);
        m.put("allowCanonicalAuthority", false);
        m.put("simulationOnly", true);
    }

    private static Map<String, Integer> zeroCounts(String... keys) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String k : keys) {
            m.put(k, 0);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static void tallyList(Map<String, Integer> counts, Object raw) {
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                counts.merge(String.valueOf(o), 1, Integer::sum);
            }
        }
    }

    private static List<Map<String, Object>> topN(Map<String, Integer> counts, int n) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .map(e -> Map.<String, Object>of("reason", e.getKey(), "count", e.getValue()))
                .toList();
    }

    private static String normalizeKey(String s) {
        if (s == null) {
            return "UNKNOWN";
        }
        return s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String normalizePolicyOutcome(String s) {
        String n = normalizeKey(s);
        if ("DATA_INSUFFICIENT".equals(n) || "DATAINSUFFICIENT".equals(n)) {
            return "DATA_INSUFFICIENT";
        }
        return n;
    }

    private static String labelFor(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "APPROVE" -> "APPROVE";
            case "APPROVE_WITH_CONDITIONS" -> "APPROVE WITH CONDITIONS";
            case "COUNTER_OFFER" -> "COUNTER OFFER";
            case "REFER" -> "REFER";
            case "DECLINE" -> "DECLINE";
            case "DATA_INSUFFICIENT" -> "DATA INSUFFICIENT";
            default -> code;
        };
    }

    private static boolean needsReview(String code) {
        return "REFER".equals(code) || "APPROVE_WITH_CONDITIONS".equals(code)
                || "COUNTER_OFFER".equals(code) || "DATA_INSUFFICIENT".equals(code);
    }

    private static String scoreDisplay(Map<String, Object> score) {
        if (STAGE_NOT_RUN.equals(String.valueOf(score.get("status"))) || score.get("score") == null) {
            return STAGE_NOT_RUN;
        }
        return score.get("score") + " · " + score.get("grade");
    }

    private static String formatInr(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return "₹" + String.format(Locale.ROOT, "%,.0f", amount);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    /** Map.copyOf rejects null values — strip them for DecisionRuntimeInput / PolicyEvaluationInput. */
    private static Map<String, Object> sanitizeMap(Map<String, Object> in) {
        if (in == null || in.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, v);
            }
        });
        return out;
    }
}
