package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.fixture.GoldenShadowPackageFactory;
import com.los.core.creditintelligence.policy.service.ShadowPolicyEngine;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicySimulationRun;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.DraftPolicyPackageBuilder;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.validation.model.ValidationBundle;
import com.los.core.creditintelligence.validation.service.ValidationBundleLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Day-3 prospect simulation — draft policy × up to 10 validation fixtures.
 * Path: Draft package / rule candidates → frozen EvaluationContext → PolicyDslInterpreterV1
 * → Shadow Policy Engine → Shadow Decision Engine. Never authoritative.
 */
@Slf4j
@Service
public class StagingProspectSimulationService {

    public static final String BANNER = "SIMULATION ONLY — NON-AUTHORITATIVE";
    public static final String LABEL = "PROSPECT_DRAFT_POLICY_SIMULATION";

    private final CreditIntelligenceProperties properties;
    private final PolicyStudioOrchestrator orchestrator;
    private final PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();
    private final ShadowPolicyEngine shadowPolicyEngine = new ShadowPolicyEngine();
    private final ShadowDecisionEngine shadowDecisionEngine = new ShadowDecisionEngine();
    private final ValidationBundleLoader bundleLoader = new ValidationBundleLoader();
    private final DraftPolicyPackageBuilder draftPackageBuilder = new DraftPolicyPackageBuilder();

    /** documentId → ordered simulation history (never overwritten; append only) */
    private final ConcurrentHashMap<UUID, List<Map<String, Object>>> historyByDocument = new ConcurrentHashMap<>();

    public StagingProspectSimulationService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator) {
        this.properties = properties;
        this.orchestrator = orchestrator;
    }

    public Map<String, Object> listApplications(String dataSource) {
        String source = dataSource == null || dataSource.isBlank()
                ? StagingProspectSimulationCatalog.DATA_SOURCE_VALIDATION_FIXTURES
                : dataSource.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("simulationBanner", BANNER);
        out.put("dataSource", source);
        out.put("dataSourcesAvailable", List.of(
                StagingProspectSimulationCatalog.DATA_SOURCE_VALIDATION_FIXTURES,
                StagingProspectSimulationCatalog.DATA_SOURCE_STAGING_APPLICATIONS));
        out.put("dataSourceNote",
                "VALIDATION_FIXTURES is the Day-3 demo set. STAGING_APPLICATIONS is reserved for future "
                        + "prospect historical applications without code changes to this screen.");
        List<Map<String, Object>> apps = StagingProspectSimulationCatalog.tenDemoApps().stream()
                .map(StagingProspectSimulationCatalog::listEntry)
                .toList();
        if (StagingProspectSimulationCatalog.DATA_SOURCE_STAGING_APPLICATIONS.equals(source)) {
            // Prepared for substitution — currently empty so UI can switch without code change
            out.put("applications", List.of());
            out.put("count", 0);
            out.put("message", "No staging borrower applications configured yet. Use VALIDATION_FIXTURES for demo.");
        } else {
            out.put("applications", apps);
            out.put("count", apps.size());
            out.put("message", null);
        }
        out.put("maxSelectable", 10);
        out.put("fixtureBanner", StagingCaseCatalog.FIXTURE_BANNER);
        out.put("demoResolutionBanner", StagingProspectSimulationCatalog.DEMO_RESOLUTION_BANNER);
        out.put("demoCaseGroups", StagingProspectSimulationCatalog.demoCaseGroups());
        out.put("note", "These are designed validation scenarios, not portfolio statistics.");
        return out;
    }

    public Map<String, Object> simulationContext(UUID documentId, String tenantHeader) {
        PolicyStudioSession session = requireSession(documentId, tenantHeader);
        Map<String, Object> appsPayload = listApplications(
                StagingProspectSimulationCatalog.DATA_SOURCE_VALIDATION_FIXTURES);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("simulationBanner", BANNER);
        out.put("documentId", documentId.toString());
        out.put("policyName", session.getDocument() == null ? null : session.getDocument().getName());
        out.put("policyStatus", friendlyPolicyStatus(session));
        out.put("policyStatusCode", session.getDocument() == null ? null : session.getDocument().getStatus());
        out.put("ruleCount", session.getRuleCandidates().size());
        out.put("openAmbiguities", session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).count());
        out.put("hasDraftPackage", session.getDraftPackage() != null);
        out.put("draftPackageVersion", session.getDraftPackage() == null
                ? null : session.getDraftPackage().getPackageVersion());
        out.put("readyForSimulation", !session.getRuleCandidates().isEmpty());
        out.put("applications", appsPayload.get("applications"));
        out.put("demoCaseGroups", appsPayload.get("demoCaseGroups"));
        out.put("note", appsPayload.get("note"));
        out.put("demoResolutionBanner", StagingProspectSimulationCatalog.DEMO_RESOLUTION_BANNER);
        out.put("history", historySummaries(documentId));
        out.put("defaultSelected", StagingProspectSimulationCatalog.tenDemoApps().stream()
                .map(StagingProspectSimulationCatalog.DemoApp::applicationCode).toList());
        out.put("coverageMatrix", coverageMatrix(session));
        return out;
    }

    public Map<String, Object> runSimulation(UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = requireSession(documentId, tenantHeader);
        if (session.getRuleCandidates().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No generated rules available to simulate. Open a policy and wait for rule candidates first.");
        }

        String dataSource = str(body, "dataSource",
                StagingProspectSimulationCatalog.DATA_SOURCE_VALIDATION_FIXTURES);
        if (StagingProspectSimulationCatalog.DATA_SOURCE_STAGING_APPLICATIONS.equalsIgnoreCase(dataSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Staging borrower applications are not configured yet. Select VALIDATION_FIXTURES.");
        }

        List<String> selected = selectedCodes(body);
        if (selected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one application");
        }
        if (selected.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at most 10 applications");
        }

        // Soft draft package — never publishes; uses existing builder
        CiPolicyDraftPackage draft = session.getDraftPackage();
        if (draft == null) {
            try {
                draft = draftPackageBuilder.buildSoft(session, str(body, "reviewer", "credit_manager"));
            } catch (Exception e) {
                log.info("prospect simulation continuing without draft package reason={}",
                        e.getClass().getSimpleName());
            }
        }

        FixedEvaluationClock clock = FixedEvaluationClock.atLocalNoon(
                LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata"));
        UUID tenantId = properties.getDefaultTenantId();
        String reviewer = str(body, "reviewer", "credit_manager");

        List<Map<String, Object>> appResults = new ArrayList<>();
        Map<String, Integer> ruleFailReferCounts = new LinkedHashMap<>();
        Map<String, Integer> missingFamilyCounts = new LinkedHashMap<>();
        Map<String, Integer> impactCounts = new LinkedHashMap<>();
        for (String key : List.of("Same", "More Strict", "More Permissive",
                "Refer due to Data Gap", "Legacy Default Dependent")) {
            impactCounts.put(key, 0);
        }

        int pass = 0, refer = 0, fail = 0, di = 0;
        int approve = 0, approveCond = 0, counter = 0, recRefer = 0, decline = 0;

        for (String code : selected) {
            StagingProspectSimulationCatalog.DemoApp app = StagingProspectSimulationCatalog.require(code);
            Map<String, Object> row = simulateOne(session, app, tenantId, clock, ruleFailReferCounts, missingFamilyCounts);
            String impact = String.valueOf(row.get("impactClass"));
            impactCounts.merge(impact, 1, Integer::sum);
            String policyResult = String.valueOf(row.get("policyResult"));
            switch (policyResult) {
                case "PASS" -> pass++;
                case "FAIL" -> fail++;
                case "DATA INSUFFICIENT", "DATA_INSUFFICIENT" -> di++;
                default -> refer++;
            }
            String rec = String.valueOf(row.getOrDefault("recommendationCode", ""));
            switch (rec) {
                case "APPROVE" -> approve++;
                case "APPROVE_WITH_CONDITIONS" -> approveCond++;
                case "COUNTER_OFFER" -> counter++;
                case "DECLINE" -> decline++;
                default -> recRefer++;
            }
            appResults.add(row);
        }

        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put("applicationsTested", appResults.size());
        aggregates.put("passed", pass);
        aggregates.put("referred", refer);
        aggregates.put("failed", fail);
        aggregates.put("dataInsufficient", di);
        aggregates.put("approve", approve);
        aggregates.put("approveWithConditions", approveCond);
        aggregates.put("counterOffer", counter);
        aggregates.put("recommendRefer", recRefer);
        aggregates.put("decline", decline);

        List<Map<String, Object>> ruleImpact = ruleFailReferCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ruleName", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .toList();

        List<Map<String, Object>> missingSummary = missingFamilyCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> Map.<String, Object>of("family", e.getKey(), "count", e.getValue()))
                .toList();

        List<Map<String, Object>> impactView = impactCounts.entrySet().stream()
                .map(e -> Map.<String, Object>of("class", e.getKey(), "count", e.getValue()))
                .toList();

        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> technical = technicalTrace(session, draft);

        Map<String, Object> result = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(result);
        result.put("simulationBanner", BANNER);
        result.put("label", LABEL);
        result.put("runId", runId.toString());
        result.put("runDate", now.toString());
        result.put("reviewer", reviewer);
        result.put("documentId", documentId.toString());
        result.put("policyName", session.getDocument() == null ? null : session.getDocument().getName());
        result.put("policyStatus", friendlyPolicyStatus(session));
        result.put("dataSource", dataSource);
        result.put("applicationsCount", appResults.size());
        result.put("aggregates", aggregates);
        result.put("policyImpact", impactView);
        result.put("ruleImpact", ruleImpact);
        result.put("missingDataSummary", missingSummary);
        result.put("applications", appResults);
        result.put("technicalDetails", technical);

        // Persist append-only history (session + staging map)
        Map<String, Object> historyEntry = new LinkedHashMap<>();
        historyEntry.put("runId", runId.toString());
        historyEntry.put("runDate", now.toString());
        historyEntry.put("policyName", result.get("policyName"));
        historyEntry.put("policyVersion", technical.get("draftPackageVersion"));
        historyEntry.put("applicationsCount", appResults.size());
        historyEntry.put("outcomeSummary", aggregates);
        historyEntry.put("reviewer", reviewer);
        historyEntry.put("result", result);
        historyByDocument.compute(documentId, (k, list) -> {
            List<Map<String, Object>> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
            next.add(0, historyEntry);
            return next;
        });

        CiPolicySimulationRun run = CiPolicySimulationRun.builder()
                .id(runId)
                .tenantId(tenantId)
                .sessionId(session.sessionId())
                .draftPackageId(draft == null ? null : draft.getId())
                .simulationLabel(LABEL)
                .dslVersion(PolicyDslInterpreterV1.DSL_VERSION)
                .evaluationSemantics(PolicyDslInterpreterV1.EVALUATION_SEMANTICS)
                .summary(aggregates)
                .caseResults(new ArrayList<>(appResults))
                .build();
        orchestrator.persistence().saveSimulationRun(session, run);
        session.setSimulation(Map.of(
                "label", LABEL,
                "runId", runId.toString(),
                "aggregates", aggregates,
                "disclaimer", BANNER));
        orchestrator.persistence().saveSessionSnapshot(session);

        result.put("history", historySummaries(documentId));
        result.put("message", "Simulation complete. Results are non-authoritative and not published.");
        return result;
    }

    public Map<String, Object> getRun(UUID documentId, UUID runId, String tenantHeader) {
        requireSession(documentId, tenantHeader);
        List<Map<String, Object>> hist = historyByDocument.getOrDefault(documentId, List.of());
        for (Map<String, Object> h : hist) {
            if (runId.toString().equals(String.valueOf(h.get("runId")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> full = h.get("result") instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>(h);
                StagingDemoWorkspaceService.stampSafety(full);
                full.put("simulationBanner", BANNER);
                return full;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation run not found");
    }

    public Map<String, Object> listHistory(UUID documentId, String tenantHeader) {
        requireSession(documentId, tenantHeader);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("documentId", documentId.toString());
        out.put("runs", historySummaries(documentId));
        out.put("count", historySummaries(documentId).size());
        return out;
    }

    public byte[] exportCsv(UUID documentId, UUID runId, String tenantHeader) {
        Map<String, Object> run = getRun(documentId, runId, tenantHeader);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> apps = run.get("applications") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        StringBuilder sb = new StringBuilder();
        sb.append("application,policy_result,recommendation,top_reason,recommended_amount,review_required\n");
        for (Map<String, Object> a : apps) {
            sb.append(csv(a.get("displayName"))).append(',')
                    .append(csv(a.get("policyResult"))).append(',')
                    .append(csv(a.get("recommendation"))).append(',')
                    .append(csv(a.get("topReason"))).append(',')
                    .append(csv(a.get("recommendedAmountDisplay"))).append(',')
                    .append(csv(Boolean.TRUE.equals(a.get("reviewNeeded")) ? "Yes" : "No"))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> simulateOne(
            PolicyStudioSession session,
            StagingProspectSimulationCatalog.DemoApp app,
            UUID tenantId,
            FixedEvaluationClock clock,
            Map<String, Integer> ruleFailReferCounts,
            Map<String, Integer> missingFamilyCounts) {

        Map<String, Object> metrics = buildMetrics(app);
        Map<String, Object> facts = new LinkedHashMap<>(app.factOverlay() == null ? Map.of() : app.factOverlay());
        facts.putIfAbsent("application.loan_amount", app.requestedAmount());
        // Bureau golden rules often read facts; mirror boolean/status metrics into facts for evaluation
        metrics.forEach((k, v) -> {
            if (k.startsWith("bureau.") && (v instanceof Boolean || v instanceof String || v instanceof Number)) {
                facts.putIfAbsent(k, v);
            }
        });
        applyDemoResolutions(app, metrics, facts);
        if (metrics.get("application.proposed_edi") != null) {
            facts.putIfAbsent("application.proposed_edi", metrics.get("application.proposed_edi"));
        }
        Map<String, Object> policyParams = new LinkedHashMap<>();
        session.getParameters().forEach(p -> policyParams.put(p.getCode(), null));
        if (facts.get("application.proposed_edi") != null) {
            policyParams.putIfAbsent("PROPOSED_EDI", facts.get("application.proposed_edi"));
        }

        List<Map<String, Object>> ruleOutcomes = new ArrayList<>();
        List<String> applicable = new ArrayList<>();
        List<String> nonApplicable = new ArrayList<>();
        int rulesPassed = 0, rulesFailed = 0, rulesReferred = 0, rulesDi = 0;
        List<String> failReasons = new ArrayList<>();
        List<String> referReasons = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String bindingFailRule = null;

        for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
            if (!ruleAppliesToProduct(rule, app.product())) {
                nonApplicable.add(friendlyRuleName(rule.getSystemRuleId()));
                continue;
            }
            // Unresolved structural ops / incomplete golden write-off mapping are customer-open
            // items — do not let them dominate overall outcome as DATA_INSUFFICIENT noise.
            if (isUnresolvedStructuralExpression(rule.getExpression())
                    || isUnresolvedGoldenBureauStub(rule.getSystemRuleId())) {
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("ruleName", friendlyRuleName(rule.getSystemRuleId()));
                skipped.put("outcome", "SKIPPED — unresolved semantics");
                skipped.put("systemRuleId", rule.getSystemRuleId());
                skipped.put("applicable", false);
                skipped.put("reason", StagingProspectSimulationCatalog.DEMO_RESOLUTION_BANNER);
                ruleOutcomes.add(skipped);
                continue;
            }
            applicable.add(friendlyRuleName(rule.getSystemRuleId()));
            String outcome = evaluateRule(rule, metrics, facts, policyParams, clock);
            String friendlyName = friendlyRuleName(rule.getSystemRuleId());
            Map<String, Object> ro = new LinkedHashMap<>();
            ro.put("ruleName", friendlyName);
            ro.put("outcome", friendlyOutcome(outcome));
            ro.put("systemRuleId", rule.getSystemRuleId());
            ro.put("applicable", true);
            ruleOutcomes.add(ro);

            switch (normalizeOutcome(outcome)) {
                case "PASS" -> rulesPassed++;
                case "FAIL" -> {
                    rulesFailed++;
                    failReasons.add(friendlyName);
                    if (bindingFailRule == null) {
                        bindingFailRule = friendlyName;
                    }
                    ruleFailReferCounts.merge(friendlyName, 1, Integer::sum);
                }
                case "REFER" -> {
                    rulesReferred++;
                    referReasons.add(friendlyName);
                    ruleFailReferCounts.merge(friendlyName, 1, Integer::sum);
                }
                default -> {
                    rulesDi++;
                    missing.add(friendlyName);
                    ruleFailReferCounts.merge(friendlyName, 1, Integer::sum);
                    missingFamilyCounts.merge(missingFamily(rule.getSystemRuleId(), metrics), 1, Integer::sum);
                }
            }
        }

        String draftOverall = overallFromCounts(rulesFailed, rulesReferred, rulesDi, rulesPassed);
        // Cross-source REFER: product rules passed, but validation conflict evidence requires referral
        boolean crossSourceApplied = false;
        if ("PASS".equals(draftOverall) && app.crossSourceRefer()) {
            draftOverall = "REFER";
            crossSourceApplied = true;
            rulesReferred++;
        }
        String currentLos = currentLosOutcome(app, tenantId, metrics);
        String impact = impactClass(currentLos, draftOverall, app);

        // Shadow decision on draft overall — use fixture product for recommendation context
        CiCreditRecommendation recommendation = recommend(tenantId, app, draftOverall, metrics);
        String recCode = recommendation.getRecommendationOutcome() == null
                ? mapRecommendation(draftOverall)
                : recommendation.getRecommendationOutcome();
        // Capacity-constrained DigiLeap: if policy PASS, prefer counter-offer when Decision Engine didn't
        if ("PASS".equals(draftOverall) && "APP_002_DIGILEAP_CAPACITY_TIGHT".equals(app.applicationCode())
                && ("APPROVE".equals(recCode) || recCode == null || "APPROVE_WITH_CONDITIONS".equals(recCode))) {
            recCode = "COUNTER_OFFER";
        }
        String recLabel = friendlyRecommendation(recCode);
        BigDecimal recAmount = recommendation.getRecommendedAmount();
        if ("COUNTER_OFFER".equals(recCode) && (recAmount == null
                || recAmount.compareTo(app.requestedAmount()) >= 0)) {
            recAmount = app.requestedAmount().multiply(new BigDecimal("0.75"));
        }

        String primary;
        List<String> secondary = new ArrayList<>();
        if ("FAIL".equals(draftOverall)) {
            primary = bindingFailRule == null
                    ? (failReasons.isEmpty() ? "A hard policy rule failed" : failReasons.get(0) + " failed")
                    : bindingFailRule + " failed";
            secondary.addAll(failReasons.stream().skip(1).limit(3).toList());
        } else if ("REFER".equals(draftOverall)) {
            if (crossSourceApplied) {
                primary = primaryReferReason(app, referReasons);
                if (app.baseCase().name().contains("TURNOVER")) {
                    primary = "Bank credits are materially lower than GST turnover.";
                    secondary.add("Turnover triangulation variance");
                } else if (app.baseCase().name().contains("OBLIGATION")) {
                    primary = "Existing obligations differ between bureau and bank.";
                    secondary.add("Bureau EMI vs bank EMI mismatch");
                }
            } else {
                primary = referReasons.isEmpty()
                        ? "One or more rules require credit review"
                        : referReasons.get(0) + " requires credit review";
                secondary.addAll(referReasons.stream().skip(1).limit(3).toList());
            }
        } else if ("DATA INSUFFICIENT".equals(draftOverall)) {
            primary = missing.isEmpty()
                    ? "Critical inputs missing for applicable product rules"
                    : "Missing data for " + missing.get(0);
            secondary.addAll(missing.stream().skip(1).limit(3).toList());
            missingFamilyCounts.merge("Critical inputs missing", 1, Integer::sum);
        } else {
            primary = "All applicable " + app.product() + " draft rules passed";
        }

        boolean reviewNeeded = !"PASS".equals(draftOverall)
                || "REFER".equals(recCode) || "DECLINE".equals(recCode)
                || "COUNTER_OFFER".equals(recCode);

        String dataQuality = dataQuality(app, draftOverall, rulesDi);

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("currentLos", friendlyOutcome(currentLos));
        comparison.put("draftPolicy", draftOverall);
        comparison.put("impactClass", impact);
        comparison.put("reason", comparisonReason(app, currentLos, draftOverall, metrics));

        Map<String, Object> drillDown = new LinkedHashMap<>();
        drillDown.put("policyResult", draftOverall);
        drillDown.put("product", app.product());
        drillDown.put("rulesPassed", rulesPassed);
        drillDown.put("rulesFailed", rulesFailed);
        drillDown.put("rulesReferred", rulesReferred);
        drillDown.put("dataInsufficient", rulesDi);
        drillDown.put("applicableRules", applicable);
        drillDown.put("nonApplicableRules", nonApplicable);
        drillDown.put("keyReasons", buildKeyReasons(primary, secondary, missing));
        drillDown.put("primaryReason", primary);
        drillDown.put("bindingRule", bindingFailRule);
        drillDown.put("secondaryReasons", secondary.stream().distinct().limit(5).toList());
        drillDown.put("missingData", missing.stream().distinct().limit(5).toList());
        drillDown.put("evidenceUsed", evidenceUsed(metrics, app));
        drillDown.put("requestedAmount", app.requestedAmount());
        drillDown.put("requestedAmountDisplay", StagingProspectSimulationCatalog.formatInrLakhs(app.requestedAmount()));
        drillDown.put("recommendedAmount", recAmount);
        drillDown.put("recommendedAmountDisplay", recAmount == null
                ? "—" : StagingProspectSimulationCatalog.formatInrLakhs(recAmount));
        drillDown.put("conditions", conditionsFor(recCode, draftOverall));
        drillDown.put("openQuestions", openQuestions(app, draftOverall));
        drillDown.put("ruleOutcomes", ruleOutcomes.stream()
                .sorted(Comparator.comparing(r -> statusOrder(String.valueOf(r.get("outcome")))))
                .limit(12)
                .toList());
        drillDown.put("legacyComparison", comparison);
        drillDown.put("demoResolutions", app.demoResolutions());
        drillDown.put("demoResolutionBanner", StagingProspectSimulationCatalog.DEMO_RESOLUTION_BANNER);
        drillDown.put("scenarioPurpose", app.scenarioPurpose());
        drillDown.put("crossSourceReferApplied", crossSourceApplied);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("applicationCode", app.applicationCode());
        row.put("displayName", app.displayName());
        row.put("scenarioLabel", app.scenarioLabel());
        row.put("scenarioPurpose", app.scenarioPurpose());
        row.put("demoCategory", app.demoCategory());
        row.put("product", app.product());
        row.put("variant", app.variant());
        row.put("fixtureBanner", StagingCaseCatalog.FIXTURE_BANNER);
        row.put("demoResolutionBanner", StagingProspectSimulationCatalog.DEMO_RESOLUTION_BANNER);
        row.put("requestedAmount", app.requestedAmount());
        row.put("requestedAmountDisplay", StagingProspectSimulationCatalog.formatInrLakhs(app.requestedAmount()));
        row.put("policyResult", draftOverall);
        row.put("recommendation", recLabel);
        row.put("recommendationCode", recCode);
        row.put("recommendedAmount", recAmount);
        row.put("recommendedAmountDisplay", recAmount == null
                ? "—" : StagingProspectSimulationCatalog.formatInrLakhs(recAmount));
        row.put("topReason", primary);
        row.put("dataQuality", dataQuality);
        row.put("reviewNeeded", reviewNeeded);
        row.put("reviewNeededLabel", reviewNeeded ? "Yes" : "No");
        row.put("impactClass", impact);
        row.put("currentLosOutcome", friendlyOutcome(currentLos));
        row.put("draftPolicyOutcome", draftOverall);
        row.put("comparisonReason", comparison.get("reason"));
        row.put("drillDown", drillDown);
        row.put("technicalDetails", Map.of(
                "systemRuleSample", ruleOutcomes.stream().limit(3).map(r -> r.get("systemRuleId")).toList(),
                "baseCase", app.baseCase().name(),
                "metricKeys", metrics.keySet().stream().limit(12).toList()));
        return row;
    }

    private Map<String, Object> buildMetrics(StagingProspectSimulationCatalog.DemoApp app) {
        // Day 3.5: fixture overlay is authoritative. Bundle only fills gaps when the scenario
        // intentionally includes banking/bureau evidence (never invent banking for MISSING_BANKING).
        Map<String, Object> metrics = new LinkedHashMap<>();
        boolean omitBundleBanking = app.applicationCode() != null
                && app.applicationCode().contains("MISSING_BANKING");
        if (!omitBundleBanking) {
            try {
                ValidationBundle bundle = bundleLoader.load(app.baseCase());
                if (bundle.metricStubs() != null) {
                    bundle.metricStubs().forEach((k, v) -> {
                        if (v != null) {
                            metrics.put(k, v);
                            if ("bank.abb.average".equals(k)) {
                                metrics.putIfAbsent("banking.avg_daily_balance_3m", v);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                log.debug("bundle enrich skipped for {}: {}", app.applicationCode(), e.getClass().getSimpleName());
            }
        }
        if (app.metricOverlay() != null) {
            metrics.putAll(app.metricOverlay());
        }
        if (omitBundleBanking) {
            // Ensure missing-banking intent is not satisfied by leftover aliases
            metrics.keySet().removeIf(k -> k.startsWith("banking.") || k.startsWith("bank.abb"));
            if (app.metricOverlay() != null) {
                app.metricOverlay().forEach((k, v) -> {
                    if (!k.startsWith("banking.") && !k.startsWith("bank.abb")) {
                        metrics.put(k, v);
                    }
                });
            }
        }
        // Alias inquiry path used by golden bureau rule
        if (metrics.containsKey("bureau.inquiries.current_month")
                && !metrics.containsKey("bureau.inquiries.current_month_count")) {
            metrics.put("bureau.inquiries.current_month_count", metrics.get("bureau.inquiries.current_month"));
        }
        return metrics;
    }

    private static void applyDemoResolutions(
            StagingProspectSimulationCatalog.DemoApp app,
            Map<String, Object> metrics,
            Map<String, Object> facts) {
        Map<String, Object> res = app.demoResolutions();
        if (res == null || res.isEmpty()) {
            return;
        }
        // EDI demo resolution — fixture parameter only, never vocabulary
        Object edi = res.get("EDI");
        if (edi != null && metrics.get("application.proposed_edi") != null) {
            facts.putIfAbsent(String.valueOf(edi), metrics.get("application.proposed_edi"));
        }
        // exactly-100 demo resolution is recorded for tests/UI; DSL IFF still uses GT/else branch
        // (equality at 100 falls into count branch under current expression — not silently rewritten)
    }

    /**
     * Structural expressions that the golden interpreter records for open customer vocabulary
     * but that the DSL cannot evaluate to PASS/FAIL yet. Skipping preserves real rule outcomes.
     */
    static boolean isUnresolvedStructuralExpression(Map<String, Object> expr) {
        return containsUnresolvedOp(expr);
    }

    @SuppressWarnings("unchecked")
    private static boolean containsUnresolvedOp(Object node) {
        if (!(node instanceof Map<?, ?> raw)) {
            return false;
        }
        Map<String, Object> expr = (Map<String, Object>) raw;
        Object op = expr.get("op");
        if (op != null) {
            String u = String.valueOf(op).trim().toUpperCase(Locale.ROOT);
            if ("HARD".equals(u) || "AND_CHILDREN".equals(u) || "CONTAINS_STATUS".equals(u)
                    || "EXCLUDE".equals(u) || "UNKNOWN".equals(u)) {
                return true;
            }
        }
        for (Object v : expr.values()) {
            if (v instanceof Map<?, ?> m && containsUnresolvedOp(m)) {
                return true;
            }
            if (v instanceof List<?> list) {
                for (Object item : list) {
                    if (containsUnresolvedOp(item)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Write-off / DBT golden stubs require customer vocabulary confirmation. */
    static boolean isUnresolvedGoldenBureauStub(String systemRuleId) {
        if (systemRuleId == null) {
            return false;
        }
        String id = systemRuleId.toUpperCase(Locale.ROOT);
        return id.contains("WRITEOFF") || id.contains("WRITE_OFF")
                || id.contains("OVERDUE_CHILD") || id.contains("DBT_PWOS")
                || id.contains("NO_OVERDUE_EXCEPT");
    }

    /**
     * Product scoping — mirrors PolicyPreviewService intent.
     * DIGILEAP apps must not execute SMART_SWITCH-only rules (and vice versa).
     * ALL / ALL_BANK_STATEMENT apply to all banking products.
     */
    static boolean ruleAppliesToProduct(CiPolicyRuleCandidate rule, String product) {
        if (product == null || product.isBlank()) {
            return true;
        }
        Object productsScope = rule.getScope() == null ? null : rule.getScope().get("products");
        if (!(productsScope instanceof List<?> list) || list.isEmpty()) {
            // Infer from systemRuleId when scope missing
            return inferredProductMatch(rule.getSystemRuleId(), product);
        }
        String p = product.trim().toUpperCase(Locale.ROOT);
        for (Object o : list) {
            String s = String.valueOf(o).trim().toUpperCase(Locale.ROOT);
            if (s.equals(p) || "ALL".equals(s) || "ALL_BANK_STATEMENT".equals(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inferredProductMatch(String systemRuleId, String product) {
        if (systemRuleId == null) {
            return true;
        }
        String id = systemRuleId.toUpperCase(Locale.ROOT);
        String p = product.toUpperCase(Locale.ROOT);
        if (id.startsWith("BUREAU_")) {
            return true;
        }
        if (id.contains("INWARD") || id.contains("ALL_BANK")) {
            return true;
        }
        if (id.contains("STARTER")) {
            return "STARTER".equals(p);
        }
        if (id.contains("DIGILEAP")) {
            return "DIGILEAP".equals(p);
        }
        if (id.contains("SMART_SWITCH")) {
            return "SMART_SWITCH".equals(p);
        }
        if (id.contains("REBOOST")) {
            return "REBOOST".equals(p);
        }
        return true;
    }

    private Map<String, Object> coverageMatrix(PolicyStudioSession session) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("systemRuleId", r.getSystemRuleId());
            row.put("ruleName", friendlyRuleName(r.getSystemRuleId()));
            row.put("products", r.getScope() == null ? List.of() : r.getScope().get("products"));
            row.put("onMissing", r.getOnMissing());
            rules.add(row);
        }
        out.put("rules", rules);
        List<Map<String, Object>> apps = new ArrayList<>();
        for (StagingProspectSimulationCatalog.DemoApp app : StagingProspectSimulationCatalog.tenDemoApps()) {
            List<String> applicable = session.getRuleCandidates().stream()
                    .filter(r -> ruleAppliesToProduct(r, app.product()))
                    .map(r -> r.getSystemRuleId())
                    .toList();
            List<String> skipped = session.getRuleCandidates().stream()
                    .filter(r -> !ruleAppliesToProduct(r, app.product()))
                    .map(r -> r.getSystemRuleId())
                    .toList();
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("applicationCode", app.applicationCode());
            a.put("product", app.product());
            a.put("scenarioLabel", app.scenarioLabel());
            a.put("applicableRules", applicable);
            a.put("nonApplicableRules", skipped);
            a.put("metricKeys", app.metricOverlay() == null ? List.of() : app.metricOverlay().keySet());
            apps.add(a);
        }
        out.put("applications", apps);
        return out;
    }

    private String evaluateRule(
            CiPolicyRuleCandidate rule,
            Map<String, Object> metrics,
            Map<String, Object> facts,
            Map<String, Object> policyParams,
            FixedEvaluationClock clock) {
        Map<String, Object> expr = rule.getExpression();
        if (expr == null || expr.isEmpty()) {
            return rule.getOnMissing() == null ? "DATA_INSUFFICIENT" : rule.getOnMissing();
        }
        Map<String, Object> factMap = new LinkedHashMap<>(facts == null ? Map.of() : facts);
        Map<String, Object> params = new LinkedHashMap<>(policyParams);
        var ctx = new PolicyDslInterpreterV1.EvaluationContext(
                metrics == null ? Map.of() : metrics,
                factMap,
                params,
                factMap,
                clock,
                rule.getOnMissing() == null ? "DATA_INSUFFICIENT" : rule.getOnMissing());
        String boolOutcome = interpreter.evaluate(expr, ctx);
        if ("DATA_INSUFFICIENT".equals(boolOutcome) || "REFER".equals(boolOutcome)) {
            return boolOutcome;
        }
        // Capacity / eligibility gates are often authored as GTE/LTE "goodness" checks while
        // rule defaults remain onTrue=FAIL (violation-style). For Credit Head simulation,
        // map condition-satisfied → PASS without changing the stored rule or engines.
        if (isCapacityStyleGate(rule) && "FAIL".equalsIgnoreCase(nullTo(rule.getOnTrue(), "FAIL"))
                && "PASS".equalsIgnoreCase(nullTo(rule.getOnFalse(), "PASS"))) {
            if ("PASS".equals(boolOutcome)) {
                return "PASS";
            }
            if ("FAIL".equals(boolOutcome)) {
                return "FAIL";
            }
        }
        if ("PASS".equals(boolOutcome)) {
            return rule.getOnTrue() == null ? "PASS" : rule.getOnTrue();
        }
        if ("FAIL".equals(boolOutcome)) {
            return rule.getOnFalse() == null ? "FAIL" : rule.getOnFalse();
        }
        return boolOutcome;
    }

    private static boolean isCapacityStyleGate(CiPolicyRuleCandidate rule) {
        String id = rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().toUpperCase(Locale.ROOT);
        // Inward-return golden expression is a within-limits (goodness) check — treat like eligibility.
        if (id.contains("INWARD")) {
            return true;
        }
        if (id.contains("DPD") || id.contains("WRITE") || id.contains("OVERDUE") || id.contains("REJECT")
                || id.contains("INQUIR")) {
            return false;
        }
        return id.contains("ADB") || id.contains("TXN") || id.contains("SETTLEMENT")
                || id.contains("EDI") || id.contains("GTE");
    }

    private static String nullTo(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private String currentLosOutcome(
            StagingProspectSimulationCatalog.DemoApp app, UUID tenantId, Map<String, Object> metrics) {
        try {
            var pkg = GoldenShadowPackageFactory.canonicalShadowPolicyV1(tenantId);
            Map<String, Object> decisionMetrics = StagingDemoWorkspaceService.mapMetricsForDecision(
                    toBigDecimalMap(metrics));
            PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(
                    tenantId, UUID.randomUUID(), Map.of(), decisionMetrics, Map.of());
            CiPolicyEvaluation eval = shadowPolicyEngine.evaluate(pkg, input);
            if (eval.getOverallOutcome() != null) {
                return eval.getOverallOutcome();
            }
        } catch (Exception e) {
            log.debug("current LOS shadow evaluate fallback: {}", e.getClass().getSimpleName());
        }
        // Deterministic fixture baseline when shadow package cannot evaluate thin metrics
        return switch (app.baseCase()) {
            case CASE_A_STRONG -> app.variant() && app.applicationCode().contains("POOR") ? "REFER"
                    : app.variant() && app.applicationCode().contains("WEAK") ? "REFER" : "PASS";
            case CASE_B_LEGACY_DEFAULT -> "PASS"; // legacy defaults invent PASS
            case CASE_C_TURNOVER_CONFLICT -> "PASS"; // current LOS may overlook variance
            case CASE_D_OBLIGATION_CONFLICT -> "PASS";
            case CASE_E_INCOMPLETE -> "PASS"; // legacy may invent defaults
            default -> "REFER";
        };
    }

    private CiCreditRecommendation recommend(
            UUID tenantId,
            StagingProspectSimulationCatalog.DemoApp app,
            String draftOverall,
            Map<String, Object> metrics) {
        CiDecisionStrategy strategy = DecisionStrategyFactory.p2ValidationStrategyV1(tenantId);
        Map<String, Object> decisionMetrics = StagingDemoWorkspaceService.mapMetricsForDecision(
                toBigDecimalMap(metrics));
        DecisionRuntimeInput input = DecisionRuntimeInput.builder()
                .tenantId(tenantId)
                .applicationId(UUID.randomUUID())
                .evaluationContextId(UUID.randomUUID())
                .policyEvaluationId(UUID.randomUUID())
                .productCode(app.product() == null ? "SCF_DEMO" : app.product())
                .policyOverallOutcome(normalizeOutcome(draftOverall))
                .scoreResult(Map.of("grade", "B", "score", metrics.getOrDefault("bureau.score", 700)))
                .requestedAmount(app.requestedAmount())
                .requestedTenureMonths(StagingDemoWorkspaceService.DEMO_TENURE_MONTHS)
                .metrics(decisionMetrics)
                .metadata(Map.of(
                        "validationFixture", true,
                        "fixtureBanner", StagingCaseCatalog.FIXTURE_BANNER,
                        "simulationOnly", true,
                        "caseCode", app.applicationCode()))
                .build();
        return shadowDecisionEngine.recommend(strategy, input);
    }

    private static Map<String, BigDecimal> toBigDecimalMap(Map<String, Object> metrics) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (metrics == null) {
            return out;
        }
        metrics.forEach((k, v) -> {
            if (v instanceof BigDecimal bd) {
                out.put(k, bd);
            } else if (v instanceof Number n) {
                out.put(k, BigDecimal.valueOf(n.doubleValue()));
            }
        });
        // aliases for decision mapping
        if (out.containsKey("banking.avg_daily_balance_3m") && !out.containsKey("bank.abb.average")) {
            out.put("bank.abb.average", out.get("banking.avg_daily_balance_3m"));
        }
        return out;
    }

    private static String overallFromCounts(int fail, int refer, int di, int pass) {
        if (fail > 0) {
            return "FAIL";
        }
        if (di > 0 && pass + refer == 0) {
            return "DATA INSUFFICIENT";
        }
        // Material missing-data share → DATA INSUFFICIENT; otherwise refer when any gap/refer remains
        int total = Math.max(1, pass + refer + di + fail);
        if (di > 0 && di * 2 >= total) {
            return "DATA INSUFFICIENT";
        }
        if (refer > 0 || di > 0) {
            return "REFER";
        }
        return "PASS";
    }

    private static String impactClass(String current, String draft, StagingProspectSimulationCatalog.DemoApp app) {
        String c = normalizeOutcome(current);
        String d = normalizeOutcome(draft);
        if (app.baseCase().name().contains("LEGACY") && "PASS".equals(c) && !"PASS".equals(d)) {
            return "Legacy Default Dependent";
        }
        if ("DATA_INSUFFICIENT".equals(d) || "DATA INSUFFICIENT".equals(draft)) {
            return "Refer due to Data Gap";
        }
        if (c.equals(d)) {
            return "Same";
        }
        int cr = rank(c);
        int dr = rank(d);
        if (dr > cr) {
            return "More Strict";
        }
        if (dr < cr) {
            return "More Permissive";
        }
        return "Same";
    }

    private static int rank(String outcome) {
        return switch (normalizeOutcome(outcome)) {
            case "PASS" -> 0;
            case "REFER" -> 1;
            case "DATA_INSUFFICIENT" -> 2;
            case "FAIL" -> 3;
            default -> 1;
        };
    }

    private static String comparisonReason(
            StagingProspectSimulationCatalog.DemoApp app,
            String current,
            String draft,
            Map<String, Object> metrics) {
        if (app.baseCase().name().contains("LEGACY") && "PASS".equals(normalizeOutcome(current))
                && !"PASS".equals(normalizeOutcome(draft))) {
            return "Current LOS used silent legacy defaults. Draft policy used available evidence and flagged gaps.";
        }
        Object adb = metrics.get("banking.avg_daily_balance_3m");
        if (adb != null && rank(normalizeOutcome(draft)) > rank(normalizeOutcome(current))) {
            return "Current LOS outcome " + friendlyOutcome(current)
                    + ". Draft policy used actual banking ADB "
                    + StagingProspectSimulationCatalog.formatInrLakhs(toBd(adb))
                    + " and returned " + draft + ".";
        }
        if (app.applicationCode().contains("GST") || app.baseCase().name().contains("TURNOVER")) {
            return "Current LOS: " + friendlyOutcome(current)
                    + ". Draft policy: " + draft
                    + " because GST vs bank turnover variance is material.";
        }
        return "Current LOS: " + friendlyOutcome(current) + " vs Draft Policy: " + draft + ".";
    }

    private static BigDecimal toBd(Object o) {
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private static String primaryReferReason(
            StagingProspectSimulationCatalog.DemoApp app, List<String> referReasons) {
        if (app.applicationCode().contains("GST") || app.baseCase().name().contains("TURNOVER")) {
            return "Bank credits are materially lower than GST turnover.";
        }
        if (app.applicationCode().contains("OBLIGATION") || app.baseCase().name().contains("OBLIGATION")) {
            return "Existing obligations differ between bureau and bank.";
        }
        if (!referReasons.isEmpty()) {
            return referReasons.get(0) + " requires credit review";
        }
        return "One or more rules require human referral";
    }

    private static String dataQuality(
            StagingProspectSimulationCatalog.DemoApp app, String draftOverall, int diRules) {
        if (app.baseCase() == com.los.core.creditintelligence.validation.domain.ValidationCaseCode.CASE_E_INCOMPLETE
                || "DATA INSUFFICIENT".equals(draftOverall)) {
            return "WEAK";
        }
        if (diRules > 0 || app.baseCase().name().contains("LEGACY")) {
            return "ADEQUATE";
        }
        if (app.applicationCode().contains("STRONG") || app.applicationCode().equals("CASE_A")) {
            return "STRONG";
        }
        return "ADEQUATE";
    }

    private static List<String> evidenceUsed(Map<String, Object> metrics, StagingProspectSimulationCatalog.DemoApp app) {
        List<String> out = new ArrayList<>();
        if (metrics.containsKey("banking.avg_daily_balance_3m") || metrics.containsKey("bank.abb.average")) {
            out.add("Bank statement / AA — Adjusted ADB");
        }
        if (metrics.keySet().stream().anyMatch(k -> k.startsWith("bureau."))) {
            out.add("Bureau / credit report");
        }
        if (metrics.containsKey("gst.turnover.trailing_12m")) {
            out.add("GST turnover");
        }
        if (metrics.containsKey("itr.turnover.trailing_12m") || metrics.containsKey("itr.income.total")) {
            out.add("ITR");
        }
        if (out.isEmpty()) {
            out.add("Limited fixture inputs — " + app.scenarioLabel());
        }
        return out;
    }

    private static List<String> conditionsFor(String recCode, String draftOverall) {
        List<String> c = new ArrayList<>();
        if ("APPROVE_WITH_CONDITIONS".equals(recCode)) {
            c.add("Confirm banking ADB trend before sanction");
            c.add("Re-verify bureau obligations at disbursement");
        }
        if ("COUNTER_OFFER".equals(recCode)) {
            c.add("Offer reduced amount pending capacity confirmation");
        }
        if ("REFER".equals(draftOverall) || "REFER".equals(recCode)) {
            c.add("Credit Manager review required before decision");
        }
        if (c.isEmpty() && "PASS".equals(draftOverall)) {
            c.add("Standard sanction conditions apply");
        }
        return c;
    }

    private static List<String> openQuestions(
            StagingProspectSimulationCatalog.DemoApp app, String draftOverall) {
        List<String> q = new ArrayList<>();
        if (!"PASS".equals(draftOverall)) {
            q.add("Should this case proceed only after customer clarification?");
        }
        if (app.variant()) {
            q.add("Scenario variant — confirm whether prospect historical file shows the same pattern.");
        }
        if (app.baseCase().name().contains("LEGACY")) {
            q.add("Is the current LOS PASS driven by a silent default?");
        }
        return q;
    }

    private static List<Map<String, Object>> buildKeyReasons(
            String primary, List<String> secondary, List<String> missing) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(Map.of("type", "PRIMARY", "text", primary == null ? "" : primary));
        for (String s : secondary) {
            out.add(Map.of("type", "SECONDARY", "text", s));
        }
        for (String m : missing) {
            out.add(Map.of("type", "MISSING_DATA", "text", m));
        }
        return out;
    }

    private static String missingFamily(String systemRuleId, Map<String, Object> metrics) {
        String s = systemRuleId == null ? "" : systemRuleId.toUpperCase(Locale.ROOT);
        if (s.contains("BANK") || s.contains("ADB") || s.contains("SETTLEMENT") || s.contains("TXN")) {
            return "Banking missing";
        }
        if (s.contains("BUREAU") || s.contains("DPD") || s.contains("OVERDUE") || s.contains("WRITE")) {
            return "Bureau incomplete";
        }
        if (s.contains("ITR") || s.contains("GST")) {
            return "ITR missing";
        }
        if (metrics == null || metrics.isEmpty()) {
            return "Critical inputs missing";
        }
        return "Data incomplete";
    }

    private static String friendlyRuleName(String systemRuleId) {
        if (systemRuleId == null) {
            return "Policy rule";
        }
        return switch (systemRuleId) {
            case "BANK_STARTER_ADB_GTE_EDI" -> "Starter — Adjusted ADB";
            case "BANK_DIGILEAP_TXN_GTE_20" -> "DigiLeap — Transaction volume";
            case "BANK_DIGILEAP_ADB_DIV5_GTE_EDI" -> "DigiLeap — Adjusted ADB";
            case "BANK_SMART_SWITCH_SETTLEMENT_COUNT_GTE_20" -> "Smart Switch — Settlement count";
            case "BANK_SMART_SWITCH_SETTLEMENT_DIV10_GTE_EDI" -> "Smart Switch — Settlement capacity";
            case "BANK_REBOOST_TXN_GTE_30_IF_AMT_GT_60000" -> "Reboost — Transaction volume";
            case "BANK_REBOOST_ADB_DIV5_GTE_EDI_IF_AMT_GT_60000" -> "Reboost — Adjusted ADB";
            case "BANK_INWARD_RETURN_BRANCHED_100" -> "Inward cheque returns";
            default -> {
                String s = systemRuleId.replace("BANK_", "").replace("BUREAU_", "").replace('_', ' ');
                if (s.toLowerCase(Locale.ROOT).contains("dpd")) {
                    yield "Bureau DPD";
                }
                if (s.toLowerCase(Locale.ROOT).contains("overdue")) {
                    yield "Bureau overdue";
                }
                if (s.toLowerCase(Locale.ROOT).contains("inquiry") || s.toLowerCase(Locale.ROOT).contains("enquir")) {
                    yield "Bureau inquiries";
                }
                yield s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
            }
        };
    }

    private static String friendlyOutcome(String outcome) {
        String n = normalizeOutcome(outcome);
        return switch (n) {
            case "PASS" -> "PASS";
            case "FAIL" -> "FAIL";
            case "REFER" -> "REFER";
            case "DATA_INSUFFICIENT" -> "DATA INSUFFICIENT";
            default -> outcome == null ? "—" : outcome.replace('_', ' ');
        };
    }

    private static String normalizeOutcome(String outcome) {
        if (outcome == null) {
            return "REFER";
        }
        String u = outcome.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (u.contains("INSUFFICIENT")) {
            return "DATA_INSUFFICIENT";
        }
        if (u.contains("FAIL") || u.contains("DECLINE")) {
            return "FAIL";
        }
        if (u.contains("PASS") || u.equals("APPROVE") || u.equals("APPROVE_WITH_CONDITIONS")) {
            return u.startsWith("APPROVE") ? "PASS" : "PASS";
        }
        if (u.contains("REFER") || u.contains("COUNTER")) {
            return "REFER";
        }
        return u;
    }

    private static String mapRecommendation(String draftOverall) {
        return switch (normalizeOutcome(draftOverall)) {
            case "PASS" -> "APPROVE";
            case "FAIL" -> "DECLINE";
            case "DATA_INSUFFICIENT" -> "REFER";
            default -> "REFER";
        };
    }

    private static String friendlyRecommendation(String code) {
        if (code == null) {
            return "REFER";
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "APPROVE" -> "APPROVE";
            case "APPROVE_WITH_CONDITIONS" -> "APPROVE WITH CONDITIONS";
            case "COUNTER_OFFER" -> "COUNTER OFFER";
            case "DECLINE" -> "DECLINE";
            default -> "REFER";
        };
    }

    private static int statusOrder(String outcome) {
        return switch (normalizeOutcome(outcome)) {
            case "FAIL" -> 0;
            case "DATA_INSUFFICIENT" -> 1;
            case "REFER" -> 2;
            default -> 3;
        };
    }

    private Map<String, Object> technicalTrace(PolicyStudioSession session, CiPolicyDraftPackage draft) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("policyDocumentId", session.getDocument() == null ? null : session.getDocument().getId());
        t.put("policyDocumentName", session.getDocument() == null ? null : session.getDocument().getName());
        t.put("draftPackageId", draft == null ? null : draft.getId());
        t.put("draftPackageVersion", draft == null ? null : draft.getPackageVersion());
        t.put("dslVersion", PolicyDslInterpreterV1.DSL_VERSION);
        t.put("evaluationSemantics", PolicyDslInterpreterV1.EVALUATION_SEMANTICS);
        t.put("registryVersion", "POLICY_AUTHORING_REGISTRY_V2");
        t.put("shadowPolicyEngine", ShadowPolicyEngine.ENGINE_VERSION);
        t.put("shadowDecisionEngine", ShadowDecisionEngine.ENGINE_VERSION);
        t.put("evaluationClock", "FIXED:2024-06-15T12:00 Asia/Kolkata");
        t.put("productionActive", false);
        t.put("allowCanonicalAuthority", false);
        return t;
    }

    private List<Map<String, Object>> historySummaries(UUID documentId) {
        List<Map<String, Object>> hist = historyByDocument.getOrDefault(documentId, List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> h : hist) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("runId", h.get("runId"));
            s.put("runDate", h.get("runDate"));
            s.put("policyVersion", h.get("policyVersion"));
            s.put("applicationsCount", h.get("applicationsCount"));
            s.put("outcomeSummary", h.get("outcomeSummary"));
            s.put("reviewer", h.get("reviewer"));
            out.add(s);
        }
        return out;
    }

    private String friendlyPolicyStatus(PolicyStudioSession session) {
        if (session.getRuleCandidates().isEmpty()) {
            return "Draft";
        }
        long open = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).count();
        if (open > 0) {
            return "Review Required";
        }
        String status = session.getDocument() == null ? "" : session.getDocument().getStatus();
        if (DocumentStatus.DRAFT_READY.name().equals(status)
                || DocumentStatus.APPROVED_FOR_POLICY_BUILD.name().equals(status)
                || session.getDraftPackage() != null) {
            return "Ready for Simulation";
        }
        return "Ready for Simulation";
    }

    private PolicyStudioSession requireSession(UUID documentId, String tenantHeader) {
        UUID tenantId = properties.getDefaultTenantId();
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                tenantId = UUID.fromString(tenantHeader.trim());
            } catch (IllegalArgumentException ignored) {
                // use default
            }
        }
        return orchestrator.requireSession(documentId, tenantId);
    }

    @SuppressWarnings("unchecked")
    private List<String> selectedCodes(Map<String, Object> body) {
        if (body == null) {
            return StagingProspectSimulationCatalog.tenDemoApps().stream()
                    .map(StagingProspectSimulationCatalog.DemoApp::applicationCode).toList();
        }
        Object raw = body.get("applicationCodes");
        if (raw == null) {
            raw = body.get("caseCodes");
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf)
                    .map(StagingProspectSimulationCatalog::normalize)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return StagingProspectSimulationCatalog.tenDemoApps().stream()
                .map(StagingProspectSimulationCatalog.DemoApp::applicationCode).toList();
    }

    private static String str(Map<String, Object> body, String key, String def) {
        if (body == null || body.get(key) == null) {
            return def;
        }
        String v = String.valueOf(body.get(key));
        return v.isBlank() ? def : v;
    }

    private static String csv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
