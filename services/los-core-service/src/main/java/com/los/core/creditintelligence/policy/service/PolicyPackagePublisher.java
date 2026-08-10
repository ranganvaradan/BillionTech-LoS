package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.CiPolicyPackageTestRun;
import com.los.core.creditintelligence.policy.domain.ExecutablePackageStatus;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes Policy Studio drafts to SHADOW executable packages only.
 * Never publishes ACTIVE.
 */
@Service
public class PolicyPackagePublisher {

    private final PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();
    private final ContentHasher hasher = new ContentHasher();

    public CiExecutablePolicyPackage fromDraft(CiPolicyDraftPackage draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "draft required");
        }
        if (draft.getCheckerApprovedAt() == null || draft.getCheckerApprovedBy() == null
                || draft.getCheckerApprovedBy().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Checker approval required before shadow publish");
        }
        if (Boolean.TRUE.equals(draft.getInvalidatedByEdit())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Draft invalidated by edit — re-approve before publish");
        }

        Map<String, Object> content = draft.getContent() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(draft.getContent());
        Map<String, Object> summary = draft.getCompletenessSummary() == null
                ? Map.of() : draft.getCompletenessSummary();

        rejectIfGate(summary, "materialAmbiguities", "Material ambiguities unresolved");
        rejectIfGate(summary, "criticalTestFailures", "Critical tests failing");
        rejectIfGate(summary, "conflicts", "Unresolved conflicts");
        rejectIfGate(summary, "missingMappings", "Missing mappings");

        // Also check content flags
        if (Boolean.TRUE.equals(content.get("hasMaterialAmbiguity"))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Material ambiguities unresolved");
        }
        if (Boolean.TRUE.equals(content.get("hasConflicts"))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unresolved conflicts");
        }
        if (Boolean.TRUE.equals(content.get("hasMissingMappings"))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Missing mappings");
        }

        Map<String, Object> testResults = runApprovedTests(content);
        boolean testsPassed = Boolean.TRUE.equals(testResults.get("passed"));
        if (!testsPassed && content.containsKey("tests")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Critical package tests failed");
        }
        String testSuiteHash = hasher.hashMap(testResults);

        String contentHash = draft.getContentHash() != null
                ? draft.getContentHash() : hasher.hashMap(content);

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("checkerApprovedBy", draft.getCheckerApprovedBy());
        approval.put("checkerApprovedAt", draft.getCheckerApprovedAt().toString());
        approval.put("shadowOnly", true);
        approval.put("activationForbidden", true);
        approval.put("neverActive", true);

        return CiExecutablePolicyPackage.builder()
                .id(UUID.randomUUID())
                .tenantId(draft.getTenantId())
                .policyCode(String.valueOf(content.getOrDefault("policyCode", "DRAFT_" + draft.getId())))
                .version(String.valueOf(draft.getPackageVersion() == null ? 1 : draft.getPackageVersion()))
                .status(ExecutablePackageStatus.SHADOW.name())
                .dslVersion(draft.getDslVersion() == null ? PolicyDslInterpreterV1.DSL_VERSION : draft.getDslVersion())
                .evaluationSemanticsVersion(PolicyDslInterpreterV1.EVALUATION_SEMANTICS)
                .orchestrationVersion(DeclarativeOrchestrator.ORCHESTRATION_VERSION)
                .content(content)
                .contentHash(contentHash)
                .testSuiteHash(testSuiteHash)
                .policyStudioDraftPackageId(draft.getId())
                .approvalMetadata(approval)
                .createdAt(Instant.now())
                .createdBy(draft.getCreatedBy())
                .publishedAt(Instant.now())
                .publishedBy(draft.getCheckerApprovedBy())
                .build();
    }

    public CiPolicyPackageTestRun recordTestRun(CiExecutablePolicyPackage pkg, String ranBy) {
        Map<String, Object> results = runApprovedTests(pkg.getContent());
        return CiPolicyPackageTestRun.builder()
                .id(UUID.randomUUID())
                .packageId(pkg.getId())
                .testSuiteHash(hasher.hashMap(results))
                .passed(Boolean.TRUE.equals(results.get("passed")))
                .results(results)
                .ranAt(Instant.now())
                .ranBy(ranBy)
                .build();
    }

    /** Explicitly refuse ACTIVE publication. */
    public CiExecutablePolicyPackage refuseActive(CiExecutablePolicyPackage pkg) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "ACTIVE publication forbidden in P1 Shadow Policy Engine");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runApprovedTests(Map<String, Object> content) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean allPassed = true;
        if (!(content.get("tests") instanceof List<?> tests) || tests.isEmpty()) {
            out.put("passed", true);
            out.put("rows", rows);
            out.put("skipped", true);
            return out;
        }
        for (Object t : tests) {
            if (!(t instanceof Map<?, ?> tm)) {
                continue;
            }
            Map<String, Object> test = (Map<String, Object>) tm;
            Map<String, Object> expr = test.get("expression") instanceof Map<?, ?> em
                    ? (Map<String, Object>) em : Map.of();
            Map<String, Object> metrics = test.get("metrics") instanceof Map<?, ?> mm
                    ? (Map<String, Object>) mm : Map.of();
            Map<String, Object> facts = test.get("facts") instanceof Map<?, ?> fm
                    ? (Map<String, Object>) fm : Map.of();
            Map<String, Object> params = test.get("policyParameters") instanceof Map<?, ?> pm
                    ? (Map<String, Object>) pm : Map.of();
            String expected = String.valueOf(test.getOrDefault("expectedOutcome", "PASS"));
            var ctx = PolicyDslInterpreterV1.EvaluationContext.of(
                    metrics, facts, params, facts, null, PolicyDslInterpreterV1.DATA_INSUFFICIENT);
            String actual = interpreter.evaluate(expr, ctx);
            boolean match = expected.equals(actual);
            if (!match) {
                allPassed = false;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", test.get("name"));
            row.put("expected", expected);
            row.put("actual", actual);
            row.put("match", match);
            rows.add(row);
        }
        out.put("passed", allPassed);
        out.put("rows", rows);
        return out;
    }

    private void rejectIfGate(Map<String, Object> summary, String key, String message) {
        Object v = summary.get(key);
        if (v instanceof Number n && n.intValue() > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }
        if (Boolean.TRUE.equals(v)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }
    }
}
