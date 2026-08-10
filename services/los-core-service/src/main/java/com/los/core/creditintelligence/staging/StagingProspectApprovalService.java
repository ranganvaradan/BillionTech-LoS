package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyReview;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.DraftPackageStatus;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.DraftPolicyPackageBuilder;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyReviewService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Day 4 prospect approval + draft package workflow — staging only, never production-active.
 */
@Slf4j
@Service
public class StagingProspectApprovalService {

    public static final String DEMO_RESOLUTION_BANNER =
            "DEMO FIXTURE RESOLUTION — CUSTOMER CONFIRMATION REQUIRED";
    public static final String DRAFT_BANNER = "DRAFT POLICY READY — NOT ACTIVE IN PRODUCTION";

    private final CreditIntelligenceProperties properties;
    private final PolicyStudioOrchestrator orchestrator;
    private final StagingProspectSimulationService simulationService;
    private final StagingPolicyStudioDemoService policyStudioDemoService;
    private final DraftPolicyPackageBuilder draftBuilder = new DraftPolicyPackageBuilder();
    private final PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();

    /** documentId → prior draft package snapshots for version comparison */
    private final ConcurrentHashMap<UUID, List<CiPolicyDraftPackage>> draftHistory = new ConcurrentHashMap<>();

    public StagingProspectApprovalService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            StagingProspectSimulationService simulationService,
            StagingPolicyStudioDemoService policyStudioDemoService) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.simulationService = simulationService;
        this.policyStudioDemoService = policyStudioDemoService;
    }

    public Map<String, Object> demoActors() {
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("actors", List.of(
                Map.of("id", "credit_manager", "displayName", "Credit Manager (maker)",
                        "role", PolicyReviewService.ROLE_CREDIT_MANAGER),
                Map.of("id", "policy_checker", "displayName", "Policy Checker",
                        "role", PolicyReviewService.ROLE_POLICY_CHECKER),
                Map.of("id", "prospect_observer", "displayName", "Prospect observer (read-only)",
                        "role", "OBSERVER")));
        out.put("makerCheckerEnabled", properties.getPolicyStudio() != null
                && properties.getPolicyStudio().isRequireMakerChecker());
        out.put("note", "Staging demo actors only — switch user for checker approval. Maker-checker is not weakened.");
        return out;
    }

    public Map<String, Object> approvalsContext(UUID documentId, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        return buildApprovalsView(session);
    }

    public Map<String, Object> submitCreditManagerApproval(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        List<String> blockers = blockingBeforeApproval(session);
        if (!blockers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot approve while blockers remain: " + String.join("; ", blockers));
        }
        String reviewer = str(body, "reviewer", "credit_manager");
        String comments = str(body, "comments", str(body, "reason",
                "Approved policy interpretation, mappings, metrics, rules, tests and simulation understanding"));
        var review = orchestrator.reviewService().review(
                session,
                "DOCUMENT",
                documentId,
                reviewer,
                PolicyReviewService.ROLE_CREDIT_MANAGER,
                ReviewState.CREDIT_MANAGER_APPROVED.name(),
                Map.of("scope", "policy_interpretation",
                        "versionHash", contentFingerprint(session)),
                comments);
        Map<String, Object> out = buildApprovalsView(session);
        out.put("lastReview", reviewSummary(review));
        out.put("message", "Credit Manager approval recorded. Checker approval still required before Draft Policy.");
        return out;
    }

    public Map<String, Object> submitCheckerApproval(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        if (!hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Credit Manager approval is required before checker approval");
        }
        List<String> blockers = blockingBeforeApproval(session);
        if (!blockers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot checker-approve while blockers remain: " + String.join("; ", blockers));
        }
        String reviewer = str(body, "reviewer", "policy_checker");
        String comments = str(body, "comments", str(body, "reason", "Checker approved draft policy package readiness"));
        try {
            var review = orchestrator.reviewService().review(
                    session,
                    "DOCUMENT",
                    documentId,
                    reviewer,
                    PolicyReviewService.ROLE_POLICY_CHECKER,
                    ReviewState.CHECKER_APPROVED.name(),
                    Map.of("scope", "draft_ready_check",
                            "versionHash", contentFingerprint(session)),
                    comments);
            Map<String, Object> out = buildApprovalsView(session);
            out.put("lastReview", reviewSummary(review));
            out.put("message", "Checker approval recorded. You may build the Draft Policy Package.");
            return out;
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                Map<String, Object> out = buildApprovalsView(session);
                out.put("makerCheckerBlocked", true);
                out.put("message", e.getReason());
                throw e;
            }
            throw e;
        }
    }

    public Map<String, Object> markSimulationReviewed(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        Map<String, Object> hist = simulationService.listHistory(documentId, tenantHeader);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = hist.get("runs") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        if (runs.isEmpty() && (session.getSimulation() == null || session.getSimulation().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Run a policy simulation before marking it reviewed");
        }
        String runId = str(body, "runId", runs.isEmpty() ? null : String.valueOf(runs.get(0).get("runId")));
        Map<String, Object> sim = session.getSimulation() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(session.getSimulation());
        if (runId != null) {
            try {
                Map<String, Object> run = simulationService.getRun(documentId, UUID.fromString(runId), tenantHeader);
                @SuppressWarnings("unchecked")
                Map<String, Object> agg = run.get("aggregates") instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
                sim.put("label", "PROSPECT_DRAFT_POLICY_SIMULATION");
                sim.put("pass", agg.getOrDefault("passed", 0));
                sim.put("refer", agg.getOrDefault("referred", 0));
                sim.put("fail", agg.getOrDefault("failed", 0));
                sim.put("dataInsufficient", agg.getOrDefault("dataInsufficient", 0));
                sim.put("applicationsTested", agg.getOrDefault("applicationsTested", 0));
                sim.put("ruleImpact", run.get("ruleImpact"));
                sim.put("policyImpact", run.get("policyImpact"));
                sim.put("runId", runId);
            } catch (Exception e) {
                log.debug("sim review enrich skipped: {}", e.getClass().getSimpleName());
            }
        }
        sim.put("simulationReviewed", true);
        sim.put("simulationReviewedBy", str(body, "reviewer", "credit_manager"));
        sim.put("simulationReviewedAt", Instant.now().toString());
        sim.put("simulationReviewedNote",
                "Simulation reviewed for draft understanding only — not production approval.");
        session.setSimulation(sim);
        Map<String, Object> out = buildApprovalsView(session);
        out.put("message", "Simulation marked reviewed (non-authoritative).");
        return out;
    }

    public Map<String, Object> buildDraftPackage(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        List<String> gates = draftBuildBlockers(session);
        if (!gates.isEmpty()) {
            Map<String, Object> out = buildApprovalsView(session);
            out.put("canBuildDraft", false);
            out.put("buildBlocked", true);
            out.put("blockers", gates);
            out.put("message", "Draft Policy Package cannot be built until blockers are cleared.");
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Draft blocked: " + String.join("; ", gates));
        }
        // Snapshot current package before rebuild for version comparison
        if (session.getDraftPackage() != null
                && !Boolean.TRUE.equals(session.getDraftPackage().getContent() == null
                ? null : session.getDraftPackage().getContent().get("rejected"))) {
            rememberDraft(documentId, copyPackage(session.getDraftPackage()));
        }
        String createdBy = str(body, "createdBy", str(body, "reviewer", "credit_manager"));
        CiPolicyDraftPackage pkg = draftBuilder.build(session, createdBy);
        pkg.setCheckerApprovedBy(latestChecker(session));
        pkg.setCheckerApprovedAt(Instant.now());
        pkg.setInvalidatedByEdit(false);
        rememberDraft(documentId, copyPackage(pkg));
        Map<String, Object> out = buildApprovalsView(session);
        out.put("draftSummary", draftSummary(session, pkg));
        out.put("message", "Draft Policy Package built. Status DRAFT_ONLY — NOT ACTIVE IN PRODUCTION.");
        out.put("draftBanner", DRAFT_BANNER);
        return out;
    }

    public Map<String, Object> compareDraftVersions(UUID documentId, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        List<CiPolicyDraftPackage> hist = draftHistory.getOrDefault(documentId, List.of());
        if (hist.size() < 2 && session.getDraftPackage() != null) {
            // Fall back to stored diffs / force business empty diff
            Map<String, Object> out = new LinkedHashMap<>();
            StagingDemoWorkspaceService.stampSafety(out);
            out.put("available", false);
            out.put("message", "Rebuild the draft after a material change to compare Draft vN vs vN+1.");
            out.put("currentVersion", session.getDraftPackage().getPackageVersion());
            return out;
        }
        CiPolicyDraftPackage from = hist.get(hist.size() - 2);
        CiPolicyDraftPackage to = hist.get(hist.size() - 1);
        Map<String, Object> raw = orchestrator.diffService().diff(session, from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("available", true);
        out.put("fromVersion", from.getPackageVersion());
        out.put("toVersion", to.getPackageVersion());
        out.put("label", "Draft v" + from.getPackageVersion() + " vs Draft v" + to.getPackageVersion());
        out.put("businessDiff", businessDiff(raw));
        out.put("technicalDiff", raw);
        return out;
    }

    public Map<String, Object> invalidateAfterMaterialEdit(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        if (session.getDraftPackage() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Build a draft package before demonstrating invalidation");
        }
        // Material edit: change a test expected outcome
        CiPolicyTestCase test = session.getTestCases().stream().findFirst().orElse(null);
        if (test != null) {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("expectedOutcome", test.getExpectedOutcome());
            changes.put("note", "Demo material edit");
            orchestrator.reviewService().review(
                    session, "TEST", test.getId(),
                    str(body, "reviewer", "credit_manager"),
                    PolicyReviewService.ROLE_CREDIT_MANAGER,
                    ReviewState.MAPPING_REVIEW.name(),
                    changes,
                    "Material edit — expected outcome re-opened for review");
        } else {
            orchestrator.reviewService().invalidateCheckerApproval(
                    session, str(body, "reviewer", "credit_manager"));
            if (session.getDraftPackage() != null) {
                session.getDraftPackage().setInvalidatedByEdit(true);
                session.getDraftPackage().setCheckerApprovedAt(null);
            }
        }
        Map<String, Object> out = buildApprovalsView(session);
        out.put("approvalInvalidated", true);
        out.put("message", "Approval invalidated because policy changed. Re-review required.");
        return out;
    }

    public Map<String, Object> listTests(UUID documentId, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("documentId", documentId.toString());
        out.put("tests", session.getTestCases().stream().map(t -> testCard(session, t)).toList());
        out.put("count", session.getTestCases().size());
        long approved = session.getTestCases().stream().filter(this::isApproved).count();
        out.put("approvedCount", approved);
        out.put("pendingCount", session.getTestCases().size() - approved);
        return out;
    }

    public Map<String, Object> reviewTest(
            UUID documentId, UUID testId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        CiPolicyTestCase test = session.testById(testId);
        if (test == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test case not found");
        }
        String uiAction = str(body, "uiAction", "APPROVE").toUpperCase(Locale.ROOT);
        String state;
        Map<String, Object> humanChanges = new LinkedHashMap<>();
        if ("REJECT".equals(uiAction) || "REJECT_TEST".equals(uiAction)) {
            state = ReviewState.REJECTED.name();
        } else if ("EDIT_EXPECTED".equals(uiAction) || "EDIT".equals(uiAction)) {
            state = ReviewState.TEST_REVIEW.name();
            if (body.get("expectedOutcome") != null) {
                humanChanges.put("expectedOutcome", body.get("expectedOutcome"));
                test.setExpectedOutcome(String.valueOf(body.get("expectedOutcome")));
            }
        } else {
            state = ReviewState.CREDIT_MANAGER_APPROVED.name();
        }
        if (body.get("humanChanges") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> humanChanges.put(String.valueOf(k), v));
        }
        var review = orchestrator.reviewService().review(
                session,
                "TEST",
                testId,
                str(body, "reviewer", "credit_manager"),
                str(body, "reviewerRole", PolicyReviewService.ROLE_CREDIT_MANAGER),
                state,
                humanChanges,
                str(body, "reason", "Prospect test review"));
        Map<String, Object> out = listTests(documentId, tenantHeader);
        out.put("lastReview", reviewSummary(review));
        out.put("approvals", buildApprovalsView(session));
        return out;
    }

    public byte[] exportRulesCsv(UUID documentId, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        StringBuilder sb = new StringBuilder();
        sb.append("rule,product,on_missing,on_true,on_false,review_status\n");
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            Object products = r.getScope() == null ? "" : r.getScope().get("products");
            sb.append(csv(r.getSystemRuleId())).append(',')
                    .append(csv(products)).append(',')
                    .append(csv(r.getOnMissing())).append(',')
                    .append(csv(r.getOnTrue())).append(',')
                    .append(csv(r.getOnFalse())).append(',')
                    .append(csv(r.getReviewStatus())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String exportPrintableHtml(UUID documentId, String tenantHeader) {
        PolicyStudioSession session = require(documentId, tenantHeader);
        Map<String, Object> approvals = buildApprovalsView(session);
        String policyName = session.getDocument() == null ? "Policy" : session.getDocument().getName();
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>")
                .append(esc(policyName)).append(" — Draft Policy Summary</title>")
                .append("<style>body{font-family:Georgia,serif;max-width:860px;margin:40px auto;color:#122;}")
                .append("h1{font-size:28px}h2{font-size:18px;margin-top:28px;border-bottom:1px solid #ccd}")
                .append(".banner{background:#fff7ed;border:1px solid #fdba74;padding:10px 14px;margin:16px 0}")
                .append("table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:6px 8px;text-align:left;font-size:13px}")
                .append(".muted{color:#556}</style></head><body>");
        html.append("<div class='banner'><strong>VALIDATION / DEMO FIXTURE CONTEXT</strong> — ")
                .append(DRAFT_BANNER).append("<br/>").append(DEMO_RESOLUTION_BANNER).append("</div>");
        html.append("<h1>").append(esc(policyName)).append("</h1>");
        html.append("<p class='muted'>Prospect draft policy summary — non-authoritative. ")
                .append("allowCanonicalAuthority=false</p>");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = approvals.get("draftSummary") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        html.append("<h2>Overview</h2><ul>");
        html.append("<li>Version: ").append(esc(String.valueOf(summary.getOrDefault("versionLabel",
                session.getDraftPackage() == null ? "—" : "Draft v" + session.getDraftPackage().getPackageVersion()))))
                .append("</li>");
        html.append("<li>Rules: ").append(session.getRuleCandidates().size()).append("</li>");
        html.append("<li>Tests: ").append(approvals.get("testsApproved")).append(" / ")
                .append(session.getTestCases().size()).append(" approved</li>");
        html.append("<li>Status: ").append(esc(String.valueOf(approvals.get("draftStatusLabel")))).append("</li>");
        html.append("<li>Production: NOT ACTIVE</li></ul>");

        html.append("<h2>Interpreted sections / rules</h2><table><tr><th>Rule</th><th>Status</th></tr>");
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            html.append("<tr><td>").append(esc(friendlyRule(r.getSystemRuleId()))).append("</td><td>")
                    .append(esc(r.getReviewStatus())).append("</td></tr>");
        }
        html.append("</table>");

        html.append("<h2>Unresolved items</h2><ul>");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocking = approvals.get("blockingItems") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        if (blocking.isEmpty()) {
            html.append("<li>None blocking</li>");
        } else {
            for (Map<String, Object> b : blocking) {
                html.append("<li>").append(esc(String.valueOf(b.get("label")))).append("</li>");
            }
        }
        html.append("</ul>");

        html.append("<h2>Simulation</h2>");
        Map<String, Object> sim = session.getSimulation() == null ? Map.of() : session.getSimulation();
        html.append("<p>Applications tested: ").append(sim.getOrDefault("applicationsTested", "—"))
                .append(" · PASS ").append(sim.getOrDefault("pass", "—"))
                .append(" · REFER ").append(sim.getOrDefault("refer", "—"))
                .append(" · FAIL ").append(sim.getOrDefault("fail", "—"))
                .append(" · DI ").append(sim.getOrDefault("dataInsufficient", "—")).append("</p>");

        html.append("<h2>Approvals</h2><ul>");
        html.append("<li>Credit Manager: ").append(hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name()) ? "✓" : "—").append("</li>");
        html.append("<li>Checker: ").append(hasDocumentApproval(session, ReviewState.CHECKER_APPROVED.name()) ? "✓" : "—").append("</li>");
        html.append("</ul>");
        html.append("<p class='muted'>Generated ").append(Instant.now()).append("</p></body></html>");
        return html.toString();
    }

    /**
     * Staging demo happy path for Banking BRE — uses fixture resolutions only.
     */
    public Map<String, Object> runDemoHappyPath(String tenantHeader) {
        Map<String, Object> banking = policyStudioDemoService.resetDemo("banking");
        UUID docId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) banking.get("policyHeader")).get("documentId")));
        PolicyStudioSession session = require(docId, tenantHeader);

        // Resolve MATERIAL ambiguities with demo fixture banner (not real vocabulary)
        for (CiPolicyAmbiguity amb : session.getAmbiguities()) {
            if (!"OPEN".equals(amb.getResolutionStatus())) {
                continue;
            }
            if (!"MATERIAL".equals(amb.getSeverity())) {
                continue;
            }
            String option = amb.getRecommendedOption() != null
                    ? amb.getRecommendedOption()
                    : (amb.getCandidateOptions() != null && !amb.getCandidateOptions().isEmpty()
                    ? String.valueOf(amb.getCandidateOptions().get(0))
                    : "DEMO_FIXTURE_RESOLUTION");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("uiAction", "ACCEPT_RECOMMENDATION");
            body.put("resolvedOption", option);
            body.put("resolvedBy", "credit_manager");
            body.put("unclearTerm", amb.getPhrase());
            body.put("notes", DEMO_RESOLUTION_BANNER);
            body.put("rememberDefinition", false);
            policyStudioDemoService.resolveAmbiguity(docId, amb.getId(), body, tenantHeader);
        }

        session = require(docId, tenantHeader);
        for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
            policyStudioDemoService.reviewRule(docId, rule.getId(), Map.of(
                    "uiAction", "APPROVE",
                    "reviewer", "credit_manager",
                    "reason", "Demo rule review"), tenantHeader);
        }
        session = require(docId, tenantHeader);
        // Governed explicit designation — not a silent bypass of data gaps
        designateCriticalDataGapsAsManual(session, "credit_manager",
                "Demo governed manual verification — customer confirmation required");
        for (CiPolicyTestCase t : session.getTestCases()) {
            reviewTest(docId, t.getId(), Map.of(
                    "uiAction", "APPROVE",
                    "reviewer", "credit_manager"), tenantHeader);
        }

        Map<String, Object> apps = simulationService.listApplications(
                StagingProspectSimulationCatalog.DATA_SOURCE_VALIDATION_FIXTURES);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> appList = apps.get("applications") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        List<String> codes = appList.stream().map(a -> String.valueOf(a.get("applicationCode"))).toList();
        Map<String, Object> sim = simulationService.runSimulation(docId, Map.of(
                "applicationCodes", codes,
                "reviewer", "credit_manager"), tenantHeader);
        markSimulationReviewed(docId, Map.of(
                "runId", sim.get("runId"),
                "reviewer", "credit_manager"), tenantHeader);

        submitCreditManagerApproval(docId, Map.of(
                "reviewer", "credit_manager",
                "comments", "Demo CM approval — " + DEMO_RESOLUTION_BANNER), tenantHeader);
        submitCheckerApproval(docId, Map.of(
                "reviewer", "policy_checker",
                "comments", "Demo checker approval — separate actor"), tenantHeader);
        Map<String, Object> built = buildDraftPackage(docId, Map.of(
                "createdBy", "credit_manager"), tenantHeader);

        Map<String, Object> out = new LinkedHashMap<>(built);
        out.put("demoPath", "HAPPY");
        out.put("documentId", docId.toString());
        out.put("demoResolutionBanner", DEMO_RESOLUTION_BANNER);
        out.put("simulationRunId", sim.get("runId"));
        return out;
    }

    /** Leave one material ambiguity open and show BLOCKED approvals. */
    public Map<String, Object> runDemoBlockedPath(String tenantHeader) {
        Map<String, Object> banking = policyStudioDemoService.resetDemo("banking");
        UUID docId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) banking.get("policyHeader")).get("documentId")));
        PolicyStudioSession session = require(docId, tenantHeader);

        CiPolicyAmbiguity leaveOpen = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity()))
                .findFirst().orElse(null);

        for (CiPolicyAmbiguity amb : session.getAmbiguities()) {
            if (!"OPEN".equals(amb.getResolutionStatus()) || !"MATERIAL".equals(amb.getSeverity())) {
                continue;
            }
            if (leaveOpen != null && amb.getId().equals(leaveOpen.getId())) {
                continue;
            }
            String option = amb.getRecommendedOption() != null
                    ? amb.getRecommendedOption() : "DEMO_FIXTURE_RESOLUTION";
            policyStudioDemoService.resolveAmbiguity(docId, amb.getId(), Map.of(
                    "uiAction", "ACCEPT_RECOMMENDATION",
                    "resolvedOption", option,
                    "resolvedBy", "credit_manager",
                    "notes", DEMO_RESOLUTION_BANNER,
                    "rememberDefinition", false), tenantHeader);
        }

        Map<String, Object> out = buildApprovalsView(require(docId, tenantHeader));
        out.put("demoPath", "BLOCKED");
        out.put("documentId", docId.toString());
        out.put("leftOpen", leaveOpen == null ? null : Map.of(
                "id", leaveOpen.getId().toString(),
                "term", leaveOpen.getPhrase(),
                "severity", leaveOpen.getSeverity()));
        out.put("message", "Approvals BLOCKED — material ambiguity unresolved. Draft cannot be built.");
        out.put("demoResolutionBanner", DEMO_RESOLUTION_BANNER);
        return out;
    }

    // ─── view builders ─────────────────────────────────────────────

    private Map<String, Object> buildApprovalsView(PolicyStudioSession session) {
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("documentId", session.getDocument().getId().toString());
        out.put("policyName", session.getDocument().getName());
        out.put("draftBanner", DRAFT_BANNER);
        out.put("demoResolutionBanner", DEMO_RESOLUTION_BANNER);
        out.put("allowCanonicalAuthority", false);
        out.put("productionActive", false);

        List<Map<String, Object>> stages = approvalStages(session);
        out.put("stages", stages);
        out.put("currentStage", stages.stream()
                .filter(s -> "CURRENT".equals(s.get("state")))
                .map(s -> s.get("key"))
                .findFirst().orElse("UPLOADED"));

        List<Map<String, Object>> checklist = readinessChecklist(session);
        out.put("readinessChecklist", checklist);
        List<Map<String, Object>> blocking = blockingItems(session);
        List<Map<String, Object>> nonBlocking = nonBlockingItems(session);
        out.put("blockingItems", blocking);
        out.put("nonBlockingItems", nonBlocking);
        out.put("blocked", !blocking.isEmpty());

        boolean cm = hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name());
        boolean checker = hasDocumentApproval(session, ReviewState.CHECKER_APPROVED.name())
                && !Boolean.TRUE.equals(session.getDraftPackage() == null
                ? false : session.getDraftPackage().getInvalidatedByEdit());
        boolean invalidated = session.getDraftPackage() != null
                && Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit());
        out.put("creditManagerApproved", cm);
        out.put("checkerApproved", checker);
        out.put("approvalInvalidated", invalidated);
        if (invalidated) {
            out.put("invalidationMessage", "Approval invalidated because policy changed. Re-review required.");
        }

        long approvedTests = session.getTestCases().stream().filter(this::isApproved).count();
        out.put("testsApproved", approvedTests);
        out.put("testsTotal", session.getTestCases().size());
        out.put("simulationReviewed", isSimulationReviewed(session));
        out.put("simulationSummary", session.getSimulation() == null ? Map.of() : session.getSimulation());

        List<String> buildBlockers = draftBuildBlockers(session);
        out.put("canBuildDraft", buildBlockers.isEmpty());
        out.put("draftBuildBlockers", buildBlockers);
        out.put("canApproveCreditManager", blockingBeforeApproval(session).isEmpty() && !cm);
        out.put("canApproveChecker", cm && !checker && blockingBeforeApproval(session).isEmpty());

        if (session.getDraftPackage() != null
                && !Boolean.TRUE.equals(session.getDraftPackage().getContent() == null
                ? null : session.getDraftPackage().getContent().get("rejected"))) {
            out.put("draftSummary", draftSummary(session, session.getDraftPackage()));
            out.put("draftStatusLabel", invalidated ? "APPROVAL INVALIDATED — RE-REVIEW REQUIRED"
                    : (checker ? "DRAFT POLICY READY" : "DRAFT PACKAGE EXISTS"));
        } else {
            out.put("draftStatusLabel", blocking.isEmpty() ? "READY FOR APPROVALS" : "BLOCKED");
        }

        out.put("reviewHistory", session.getReviews().stream()
                .sorted(Comparator.comparing(CiPolicyReview::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(12)
                .map(this::reviewSummary)
                .toList());
        out.put("demoActors", demoActors().get("actors"));
        out.put("makerCheckerEnabled", properties.getPolicyStudio() != null
                && properties.getPolicyStudio().isRequireMakerChecker());
        out.put("lifecycleNote",
                "After Checker approval: Approve Policy → Schedule Policy. "
                        + "Business ACTIVE ≠ production authority (allowCanonicalAuthority=false).");
        out.put("activateCanonicalAuthorityExposed", false);
        return out;
    }

    private List<Map<String, Object>> approvalStages(PolicyStudioSession session) {
        boolean uploaded = session.getDocument() != null;
        boolean interpreted = !session.getInterpretations().isEmpty();
        long openMat = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity())).count();
        boolean ambReviewed = openMat == 0;
        long rulesApproved = session.getRuleCandidates().stream()
                .filter(r -> ReviewState.CREDIT_MANAGER_APPROVED.name().equals(r.getReviewStatus())
                        || ReviewState.CHECKER_APPROVED.name().equals(r.getReviewStatus())).count();
        boolean rulesReviewed = !session.getRuleCandidates().isEmpty()
                && rulesApproved >= Math.max(1, session.getRuleCandidates().size() / 2);
        Map<String, Object> implAssess = new PolicyImplementabilityService().assess(session);
        Map<String, Object> implSum = implAssess.get("summary") instanceof Map<?, ?> im
                ? castMap(im) : Map.of();
        boolean dataReady = !Boolean.TRUE.equals(implSum.get("draftBlockedByCriticalDataGap"))
                && asInt(implSum.get("implementationReadinessPercent")) >= 40;
        long testsApproved = session.getTestCases().stream().filter(this::isApproved).count();
        boolean testsReviewed = !session.getTestCases().isEmpty()
                && testsApproved >= Math.max(1, session.getTestCases().size() / 2);
        boolean simDone = session.getSimulation() != null && !session.getSimulation().isEmpty()
                && (session.getSimulation().containsKey("pass")
                || session.getSimulation().containsKey("runId")
                || Boolean.TRUE.equals(session.getSimulation().get("simulationReviewed")));
        boolean simReviewed = isSimulationReviewed(session);
        boolean cm = hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name());
        boolean checker = hasDocumentApproval(session, ReviewState.CHECKER_APPROVED.name())
                && !(session.getDraftPackage() != null
                && Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit()));
        boolean draftReady = session.getDraftPackage() != null
                && !Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit())
                && checker
                && draftBuilder.rejectionReasons(session).isEmpty();

        List<StageDef> defs = List.of(
                new StageDef("UPLOADED", "Policy Uploaded", uploaded),
                new StageDef("INTERPRETED", "AI Understanding", interpreted),
                new StageDef("AMBIGUITIES", "Ambiguous Terms", ambReviewed),
                new StageDef("RULES", "Business Rules", rulesReviewed),
                new StageDef("DATA_READINESS", "Data Readiness", dataReady),
                new StageDef("TESTS", "Tests", testsReviewed),
                new StageDef("SIMULATION", "Simulation", simDone && simReviewed),
                new StageDef("CM_APPROVAL", "Credit Manager Approval", cm),
                new StageDef("CHECKER_APPROVAL", "Checker Approval", checker),
                new StageDef("DRAFT_READY", "Draft Ready", draftReady)
        );
        List<Map<String, Object>> stages = new ArrayList<>();
        boolean currentSet = false;
        for (StageDef d : defs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", d.key);
            row.put("label", d.label);
            if (d.done) {
                row.put("state", "DONE");
            } else if (!currentSet) {
                row.put("state", "CURRENT");
                currentSet = true;
            } else {
                row.put("state", "PENDING");
            }
            stages.add(row);
        }
        if (!currentSet && !stages.isEmpty()) {
            stages.get(stages.size() - 1).put("state", "DONE");
        }
        return stages;
    }

    private List<Map<String, Object>> readinessChecklist(PolicyStudioSession session) {
        long openMat = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity())).count();
        long openAll = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).count();
        long blockedRules = session.getRuleCandidates().stream()
                .filter(r -> r.getValidationErrors() != null && !r.getValidationErrors().isEmpty()).count();
        long approvedTests = session.getTestCases().stream().filter(this::isApproved).count();
        long blockingConflicts = session.getConflicts().stream()
                .filter(c -> Boolean.TRUE.equals(c.get("blocking"))).count();
        boolean simReviewed = isSimulationReviewed(session);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(check("clauses", session.getClauses().size() + " clauses interpreted",
                session.getClauses().isEmpty() ? "RED" : "GREEN"));
        list.add(check("rules", session.getRuleCandidates().size() + " rules generated",
                session.getRuleCandidates().isEmpty() ? "RED" : "GREEN"));
        list.add(check("rulesBlocked", blockedRules + " rules blocked",
                blockedRules == 0 ? "GREEN" : "RED"));
        list.add(check("ambiguities", openMat + " material ambiguities unresolved"
                        + (openAll > openMat ? " (" + openAll + " open total)" : ""),
                openMat == 0 ? (openAll == 0 ? "GREEN" : "AMBER") : "RED"));
        list.add(check("metrics", session.getMetricCandidates().size() + " metric candidates",
                session.getMetricCandidates().isEmpty() ? "AMBER" : "GREEN"));
        list.add(check("tests", approvedTests + " / " + session.getTestCases().size() + " tests approved",
                session.getTestCases().isEmpty() ? "RED"
                        : (approvedTests == 0 ? "RED"
                        : (approvedTests < session.getTestCases().size() ? "AMBER" : "GREEN"))));
        list.add(check("simulation", simReviewed ? "Simulation run completed & reviewed"
                        : "Simulation not reviewed",
                simReviewed ? "GREEN" : "RED"));
        list.add(check("conflicts", blockingConflicts + " blocking item"
                        + (blockingConflicts == 1 ? "" : "s"),
                blockingConflicts == 0 ? "GREEN" : "RED"));
        Map<String, Object> impl = new PolicyImplementabilityService().assess(session);
        Map<String, Object> implSummary = impl.get("summary") instanceof Map<?, ?> m
                ? castMap(m) : Map.of();
        int implPct = asInt(implSummary.get("implementationReadinessPercent"));
        boolean criticalGap = Boolean.TRUE.equals(implSummary.get("draftBlockedByCriticalDataGap"));
        list.add(check("dataReadiness",
                "Data readiness " + implPct + "%"
                        + (criticalGap ? " — critical rule data gap" : ""),
                criticalGap ? "RED" : (implPct >= 70 ? "GREEN" : "AMBER")));
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Map<String, Object>> blockingItems(PolicyStudioSession session) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (CiPolicyAmbiguity a : session.getAmbiguities()) {
            if ("OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity())) {
                items.add(Map.of(
                        "key", "amb-" + a.getId(),
                        "label", (a.getPhrase() == null ? "Ambiguity" : a.getPhrase()) + " unresolved",
                        "detail", "Material ambiguity must be resolved before approval"));
            }
        }
        session.getConflicts().stream()
                .filter(c -> Boolean.TRUE.equals(c.get("blocking")))
                .forEach(c -> items.add(Map.of(
                        "key", "conflict-" + c.getOrDefault("id", c.hashCode()),
                        "label", String.valueOf(c.getOrDefault("message", "Blocking conflict")),
                        "detail", "Blocking conflict")));
        if (!isSimulationReviewed(session)) {
            items.add(Map.of("key", "sim", "label", "Simulation not reviewed",
                    "detail", "Mark Simulation Reviewed after a 10-app run"));
        }
        long approvedTests = session.getTestCases().stream().filter(this::isApproved).count();
        if (!session.getTestCases().isEmpty() && approvedTests == 0) {
            items.add(Map.of("key", "tests", "label", "Critical rule tests not approved",
                    "detail", "Approve generated tests before draft build"));
        }
        if (session.getDraftPackage() != null
                && Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit())) {
            items.add(Map.of("key", "invalidated",
                    "label", "Prior checker approval invalidated by material edit",
                    "detail", "Re-review required"));
        }
        Map<String, Object> impl = new PolicyImplementabilityService().assess(session);
        Map<String, Object> implSummary = impl.get("summary") instanceof Map<?, ?> m
                ? castMap(m) : Map.of();
        if (Boolean.TRUE.equals(implSummary.get("draftBlockedByCriticalDataGap"))) {
            items.add(Map.of(
                    "key", "data-readiness-critical",
                    "label", "Critical rule data requirements unresolved",
                    "detail", "Resolve Data Readiness gaps for knockout / hard eligibility rules before draft build"));
        }
        for (String r : draftBuilder.rejectionReasons(session)) {
            if (items.stream().noneMatch(i -> String.valueOf(i.get("label")).contains(r))) {
                items.add(Map.of("key", "reject-" + r.hashCode(), "label", r, "detail", "Draft gate"));
            }
        }
        return items;
    }

    private List<Map<String, Object>> nonBlockingItems(PolicyStudioSession session) {
        List<Map<String, Object>> items = new ArrayList<>();
        session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && !"MATERIAL".equals(a.getSeverity()))
                .forEach(a -> items.add(Map.of(
                        "key", "nb-amb-" + a.getId(),
                        "label", (a.getPhrase() == null ? "Optional ambiguity" : a.getPhrase()) + " open",
                        "detail", "Non-material — does not block draft")));
        items.add(Map.of("key", "nb-tech", "label", "Optional technical mapping notes",
                "detail", "Unused source mappings can be refined later"));
        items.add(Map.of("key", "nb-docs", "label", "Additional documentation recommendation",
                "detail", "Customer confirmation still required for demo fixture resolutions"));
        return items;
    }

    private List<String> blockingBeforeApproval(PolicyStudioSession session) {
        return blockingItems(session).stream()
                .map(i -> String.valueOf(i.get("label")))
                .toList();
    }

    private List<String> draftBuildBlockers(PolicyStudioSession session) {
        List<String> blockers = new ArrayList<>();
        blockingItems(session).forEach(i -> blockers.add(String.valueOf(i.get("label"))));
        if (!hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name())) {
            blockers.add("Credit Manager approval required");
        }
        if (!hasDocumentApproval(session, ReviewState.CHECKER_APPROVED.name())) {
            blockers.add("Checker approval required");
        }
        if (session.getDraftPackage() != null
                && Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit())) {
            blockers.add("Re-approve after material edit invalidation");
        }
        for (String r : draftBuilder.rejectionReasons(session)) {
            if (!blockers.contains(r)) {
                blockers.add(r);
            }
        }
        return blockers;
    }

    private Map<String, Object> draftSummary(PolicyStudioSession session, CiPolicyDraftPackage pkg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyName", session.getDocument().getName());
        m.put("versionLabel", "Draft v" + pkg.getPackageVersion());
        m.put("packageVersion", pkg.getPackageVersion());
        m.put("rules", session.getRuleCandidates().size());
        long products = session.getClauses().stream()
                .map(c -> c.getProductScope()).filter(p -> p != null && !p.isBlank()).distinct().count();
        m.put("products", products);
        long openMat = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity())).count();
        m.put("blockingAmbiguities", openMat);
        long approvedTests = session.getTestCases().stream().filter(this::isApproved).count();
        m.put("testsApproved", approvedTests);
        m.put("testsTotal", session.getTestCases().size());
        m.put("applicationsSimulated", session.getSimulation() == null
                ? 0 : session.getSimulation().getOrDefault("applicationsTested", 0));
        m.put("creditManagerApproved", hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name()));
        m.put("checkerApproved", hasDocumentApproval(session, ReviewState.CHECKER_APPROVED.name()));
        m.put("status", "DRAFT POLICY READY");
        m.put("packageStatus", DraftPackageStatus.DRAFT_ONLY.name());
        m.put("production", "NOT ACTIVE");
        m.put("productionActive", false);
        m.put("contentHash", pkg.getContentHash());
        m.put("dslVersion", pkg.getDslVersion());
        m.put("dependencyGraphHash", pkg.getDependencyGraphHash());
        m.put("banner", DRAFT_BANNER);
        return m;
    }

    private Map<String, Object> testCard(PolicyStudioSession session, CiPolicyTestCase t) {
        CiPolicyRuleCandidate rule = t.getRuleCandidateId() == null ? null
                : session.ruleById(t.getRuleCandidateId());
        String actual = evaluateTest(rule, t);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("name", t.getName());
        m.put("rule", friendlyRule(rule == null ? null : rule.getSystemRuleId()));
        m.put("systemRuleId", rule == null ? null : rule.getSystemRuleId());
        m.put("scenario", t.getName());
        m.put("inputs", mergeInputs(t));
        m.put("expectedOutcome", t.getExpectedOutcome());
        m.put("actualOutcome", actual);
        m.put("status", statusForTest(t, actual));
        m.put("boundaryCase", Boolean.TRUE.equals(t.getBoundaryCase()));
        m.put("reviewStatus", t.getReviewStatus());
        m.put("approved", isApproved(t));
        return m;
    }

    private String evaluateTest(CiPolicyRuleCandidate rule, CiPolicyTestCase t) {
        if (rule == null || rule.getExpression() == null || rule.getExpression().isEmpty()) {
            return t.getExpectedOutcome() == null ? "DATA_INSUFFICIENT" : t.getExpectedOutcome();
        }
        if (StagingProspectSimulationService.isUnresolvedStructuralExpression(rule.getExpression())
                || StagingProspectSimulationService.isUnresolvedGoldenBureauStub(rule.getSystemRuleId())) {
            return t.getExpectedOutcome() == null ? "DATA_INSUFFICIENT" : t.getExpectedOutcome();
        }
        Map<String, Object> metrics = t.getInputMetrics() == null ? Map.of() : t.getInputMetrics();
        Map<String, Object> facts = new LinkedHashMap<>(t.getInputFacts() == null ? Map.of() : t.getInputFacts());
        metrics.forEach((k, v) -> {
            if (k.startsWith("bureau.") || k.startsWith("application.")) {
                facts.putIfAbsent(k, v);
            }
        });
        FixedEvaluationClock clock = FixedEvaluationClock.atLocalNoon(
                LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata"));
        var ctx = new PolicyDslInterpreterV1.EvaluationContext(
                metrics, facts, Map.of(), facts, clock,
                rule.getOnMissing() == null ? "DATA_INSUFFICIENT" : rule.getOnMissing());
        String boolOutcome = interpreter.evaluate(rule.getExpression(), ctx);
        if ("DATA_INSUFFICIENT".equals(boolOutcome) || "REFER".equals(boolOutcome)) {
            return boolOutcome;
        }
        if ("PASS".equals(boolOutcome)) {
            return rule.getOnTrue() == null ? "PASS" : rule.getOnTrue();
        }
        if ("FAIL".equals(boolOutcome)) {
            return rule.getOnFalse() == null ? "FAIL" : rule.getOnFalse();
        }
        return boolOutcome;
    }

    private static Map<String, Object> mergeInputs(CiPolicyTestCase t) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (t.getInputMetrics() != null) {
            m.putAll(t.getInputMetrics());
        }
        if (t.getInputFacts() != null) {
            t.getInputFacts().forEach(m::putIfAbsent);
        }
        return m;
    }

    private static String statusForTest(CiPolicyTestCase t, String actual) {
        if (isApprovedStatic(t)) {
            return "APPROVED";
        }
        if (ReviewState.REJECTED.name().equals(t.getReviewStatus())) {
            return "REJECTED";
        }
        if (actual != null && t.getExpectedOutcome() != null
                && actual.replace(' ', '_').equalsIgnoreCase(t.getExpectedOutcome().replace(' ', '_'))) {
            return "MATCH";
        }
        return "PENDING";
    }

    private static boolean isApprovedStatic(CiPolicyTestCase t) {
        return ReviewState.CHECKER_APPROVED.name().equals(t.getReviewStatus())
                || ReviewState.CREDIT_MANAGER_APPROVED.name().equals(t.getReviewStatus())
                || "APPROVED".equals(t.getReviewStatus());
    }

    private boolean isApproved(CiPolicyTestCase t) {
        return isApprovedStatic(t);
    }

    private boolean isSimulationReviewed(PolicyStudioSession session) {
        return session.getSimulation() != null
                && Boolean.TRUE.equals(session.getSimulation().get("simulationReviewed"));
    }

    private boolean hasDocumentApproval(PolicyStudioSession session, String state) {
        return session.getReviews().stream()
                .anyMatch(r -> "DOCUMENT".equalsIgnoreCase(r.getSubjectType())
                        && state.equals(r.getReviewState()));
    }

    private String latestChecker(PolicyStudioSession session) {
        return session.getReviews().stream()
                .filter(r -> ReviewState.CHECKER_APPROVED.name().equals(r.getReviewState()))
                .reduce((a, b) -> b)
                .map(CiPolicyReview::getReviewer)
                .orElse("policy_checker");
    }

    private Map<String, Object> reviewSummary(CiPolicyReview r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reviewId", r.getId() == null ? null : r.getId().toString());
        m.put("reviewer", r.getReviewer());
        m.put("reviewerRole", r.getReviewerRole());
        m.put("reviewState", r.getReviewState());
        m.put("subjectType", r.getSubjectType());
        m.put("reason", r.getReason());
        m.put("timestamp", r.getCreatedAt() == null ? Instant.now().toString() : r.getCreatedAt().toString());
        m.put("versionHash", r.getHumanChanges() == null ? null : r.getHumanChanges().get("versionHash"));
        return m;
    }

    private static Map<String, Object> check(String key, String label, String tone) {
        String icon = switch (tone) {
            case "GREEN" -> "✓";
            case "RED" -> "✕";
            default -> "⚠";
        };
        return Map.of("key", key, "label", label, "tone", tone, "icon", icon,
                "display", icon + " " + label);
    }

    private static List<Map<String, Object>> businessDiff(Map<String, Object> raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addDiffRows(rows, "Rule added", raw.get("addedRule"));
        addDiffRows(rows, "Rule removed", raw.get("removedRule"));
        addDiffRows(rows, "Threshold changed", raw.get("changedThreshold"));
        addDiffRows(rows, "Product scope changed", raw.get("changedScope"));
        addDiffRows(rows, "Metric changed", raw.get("changedMetricMapping"));
        addDiffRows(rows, "Ambiguity resolution changed", raw.get("changedAmbiguityResolution"));
        addDiffRows(rows, "Test expectation changed", raw.get("changedExpectedTestOutcome"));
        if (rows.isEmpty()) {
            rows.add(Map.of("type", "No material business changes detected", "detail", "—"));
        }
        return rows;
    }

    private static void addDiffRows(List<Map<String, Object>> rows, String type, Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            for (Object o : list) {
                rows.add(Map.of("type", type, "detail", String.valueOf(o)));
            }
        }
    }

    private void rememberDraft(UUID documentId, CiPolicyDraftPackage pkg) {
        draftHistory.compute(documentId, (k, v) -> {
            List<CiPolicyDraftPackage> list = v == null ? new ArrayList<>() : new ArrayList<>(v);
            list.add(pkg);
            if (list.size() > 8) {
                list = new ArrayList<>(list.subList(list.size() - 8, list.size()));
            }
            return list;
        });
    }

    private static CiPolicyDraftPackage copyPackage(CiPolicyDraftPackage src) {
        return CiPolicyDraftPackage.builder()
                .id(src.getId())
                .tenantId(src.getTenantId())
                .policyDocumentId(src.getPolicyDocumentId())
                .sessionId(src.getSessionId())
                .packageStatus(src.getPackageStatus())
                .packageVersion(src.getPackageVersion())
                .dslVersion(src.getDslVersion())
                .dependencyGraphHash(src.getDependencyGraphHash())
                .content(src.getContent() == null ? Map.of() : new LinkedHashMap<>(src.getContent()))
                .contentHash(src.getContentHash())
                .completenessStatus(src.getCompletenessStatus())
                .completenessSummary(src.getCompletenessSummary())
                .approvalHistory(src.getApprovalHistory())
                .createdBy(src.getCreatedBy())
                .checkerApprovedAt(src.getCheckerApprovedAt())
                .checkerApprovedBy(src.getCheckerApprovedBy())
                .invalidatedByEdit(src.getInvalidatedByEdit())
                .build();
    }

    private String contentFingerprint(PolicyStudioSession session) {
        return "v" + session.getRuleCandidates().size()
                + "-a" + session.getAmbiguities().stream().filter(a -> "RESOLVED".equals(a.getResolutionStatus())).count()
                + "-t" + session.getTestCases().stream().filter(this::isApproved).count();
    }

    private PolicyStudioSession require(UUID documentId, String tenantHeader) {
        UUID tenantId = properties.getDefaultTenantId();
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                tenantId = UUID.fromString(tenantHeader.trim());
            } catch (Exception ignored) {
                // keep default
            }
        }
        return orchestrator.requireSession(documentId, tenantId);
    }

    /**
     * Explicit governed conversion of blocking critical data gaps to MANUAL_VERIFICATION.
     * Not a silent bypass — designations remain visible in the implementability package.
     */
    private void designateCriticalDataGapsAsManual(
            PolicyStudioSession session, String actor, String reason) {
        Map<String, Object> assess = new PolicyImplementabilityService().assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = assess.get("rules") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        Set<String> blockedCriticalIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : rules) {
            if (!Boolean.TRUE.equals(row.get("critical"))) {
                continue;
            }
            String st = String.valueOf(row.get("implementability"));
            if (PolicyImplementabilityService.DATA_SOURCE_REQUIRED.equals(st)
                    || PolicyImplementabilityService.MAPPING_REQUIRED.equals(st)
                    || PolicyImplementabilityService.METRIC_REQUIRED.equals(st)
                    || PolicyImplementabilityService.DEFINITION_REQUIRED.equals(st)
                    || PolicyImplementabilityService.NOT_IMPLEMENTABLE.equals(st)
                    || PolicyImplementabilityService.RECONCILIATION_REQUIRED.equals(st)) {
                if (row.get("ruleId") != null) {
                    blockedCriticalIds.add(String.valueOf(row.get("ruleId")));
                }
            }
        }
        if (blockedCriticalIds.isEmpty()) {
            return;
        }
        for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
            if (rule.getId() == null || !blockedCriticalIds.contains(rule.getId().toString())) {
                continue;
            }
            Map<String, Object> meta = rule.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(rule.getMetadata());
            meta.put("verificationMode", "MANUAL");
            meta.put("dataGapDisposition", "MANUAL_VERIFICATION");
            meta.put("requiredEvidence", "Credit appraisal note / supporting document");
            meta.put("requiredActor", "Credit Manager");
            meta.put("manualOutcome", "PASS / FAIL / REFER");
            meta.put("designatedBy", actor);
            meta.put("designationReason", reason);
            meta.put("designatedAt", Instant.now().toString());
            rule.setMetadata(meta);
        }
        orchestrator.persistence().saveSessionSnapshot(session);
    }

    private static String friendlyRule(String id) {
        if (id == null) {
            return "Policy rule";
        }
        return id.replace("BANK_", "").replace("BUREAU_", "").replace('_', ' ');
    }

    private static String str(Map<String, Object> body, String key, String def) {
        if (body == null || body.get(key) == null) {
            return def;
        }
        String v = String.valueOf(body.get(key)).trim();
        return v.isEmpty() || "null".equals(v) ? def : v;
    }

    private static String csv(Object o) {
        String s = o == null ? "" : String.valueOf(o).replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record StageDef(String key, String label, boolean done) {}
}
