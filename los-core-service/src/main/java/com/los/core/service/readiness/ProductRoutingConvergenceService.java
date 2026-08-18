package com.los.core.service.readiness;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.underwriting.ScorecardGovernanceStatuses;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PRODUCT-CONFIG-ROUTING-CONVERGENCE-1 — orchestration/read-model over existing runtime resolvers.
 * Does NOT create a new routing engine.
 */
@Service
@RequiredArgsConstructor
public class ProductRoutingConvergenceService {

    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final UnderwritingRuleSetRepository ruleSetRepository;
    private final UnderwritingScorecardRepository scorecardRepository;

    public Map<String, Object> resolveRuntime(
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal amount) {
        LoanApplication app = syntheticApp(borrowerType, loanProduct, intakeSegment, amount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("authority", "EXISTING_RUNTIME_RESOLVERS");
        out.put("productionAuthority", "LIVE_UW_PATH");

        String segment = intakeSegment == null ? IntakeSegment.BORROWER.name() : intakeSegment;
        List<WorkflowConfig> wfPeers = borrowerType == null || loanProduct == null
                ? List.of()
                : workflowConfigRepository
                        .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                                borrowerType, loanProduct, segment);
        // Catalog listing only — never persist or select a default onto an application.
        Optional<WorkflowConfig> wf = activeWorkflowConfigService.findActiveForApplication(app);
        out.put("workflow", workflowResolution(wf.orElse(null), wfPeers));

        List<UnderwritingRuleSet> ruleCands = borrowerType == null || loanProduct == null
                ? List.of()
                : ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        borrowerType, loanProduct);
        UnderwritingRuleSet topRule = ruleCands.isEmpty() ? null : ruleCands.get(0);
        out.put("liveRuleSet", ruleResolution(topRule, ruleCands));

        List<UnderwritingScorecard> scCands = borrowerType == null || loanProduct == null
                ? List.of()
                : scorecardRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        borrowerType, loanProduct);
        UnderwritingScorecard topSc = firstScopeMatch(scCands, amount);
        out.put("scorecard", scorecardResolution(topSc, scCands));

        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("workflowId", wf.map(w -> w.getId().toString()).orElse(null));
        snap.put("workflowVersion", wf.map(WorkflowConfig::getVersion).orElse(null));
        snap.put("ruleSetId", topRule == null || topRule.getId() == null ? null : topRule.getId().toString());
        snap.put("ruleSetPriority", topRule == null ? null : topRule.getPriority());
        snap.put("scorecardId", topSc == null || topSc.getId() == null ? null : topSc.getId().toString());
        snap.put("scorecardVersion", topSc == null ? null : topSc.getVersion());
        snap.put("scorecardStatus", topSc == null ? null : topSc.getStatus());
        snap.put("note", "Foundation for future underwriting configuration snapshot — not a new engine");
        out.put("configurationSnapshotFoundation", snap);
        return out;
    }

    public Map<String, Object> compareSelectionToRuntime(
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal amount,
            UUID selectedWorkflowId,
            UUID selectedRuleSetId,
            UUID selectedScorecardId) {
        Map<String, Object> runtime = resolveRuntime(borrowerType, loanProduct, intakeSegment, amount);
        List<String> mismatches = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("runtime", runtime);

        UUID runtimeWf = idOf(nested(runtime, "workflow"));
        UUID runtimeRule = idOf(nested(runtime, "liveRuleSet"));
        UUID runtimeSc = idOf(nested(runtime, "scorecard"));

        if (selectedWorkflowId != null) {
            if (runtimeWf == null) {
                keys.add("WORKFLOW_ROUTING_MISMATCH");
                mismatches.add("WORKFLOW_ROUTING_MISMATCH: Product Configuration workflowId="
                        + selectedWorkflowId + " but runtime resolver returned NO_MATCH");
            } else if (!Objects.equals(selectedWorkflowId, runtimeWf)) {
                keys.add("WORKFLOW_ROUTING_MISMATCH");
                mismatches.add("WORKFLOW_ROUTING_MISMATCH: Product Configuration workflowId="
                        + selectedWorkflowId + " disagrees with runtime workflowId=" + runtimeWf);
            }
        }
        if (selectedRuleSetId != null) {
            if (runtimeRule == null) {
                keys.add("RULESET_ROUTING_MISMATCH");
                mismatches.add("RULESET_ROUTING_MISMATCH: Product Configuration liveRuleSetId="
                        + selectedRuleSetId + " but runtime resolver returned NO_MATCH");
            } else if (!Objects.equals(selectedRuleSetId, runtimeRule)) {
                keys.add("RULESET_ROUTING_MISMATCH");
                mismatches.add("RULESET_ROUTING_MISMATCH: Product Configuration liveRuleSetId="
                        + selectedRuleSetId + " disagrees with runtime top-priority ruleSetId=" + runtimeRule);
            }
        }
        if (selectedScorecardId != null) {
            if (runtimeSc == null) {
                keys.add("SCORECARD_ROUTING_MISMATCH");
                mismatches.add("SCORECARD_ROUTING_MISMATCH: Product Configuration scorecardId="
                        + selectedScorecardId
                        + " but ScorecardPolicyEngine returned NO_MATCH for amount/scope (same resolver)");
            } else if (!Objects.equals(selectedScorecardId, runtimeSc)) {
                keys.add("SCORECARD_ROUTING_MISMATCH");
                mismatches.add("SCORECARD_ROUTING_MISMATCH: Product Configuration scorecardId="
                        + selectedScorecardId + " disagrees with runtime scorecardId=" + runtimeSc);
            }
        }

        Map<String, Object> scSel = nested(runtime, "scorecard");
        if (Boolean.FALSE.equals(scSel.get("runtimeActive")) && scSel.get("id") != null) {
            keys.add("SCORECARD_NOT_RUNTIME_ACTIVE");
            mismatches.add("SCORECARD_NOT_RUNTIME_ACTIVE: resolved scorecard is not ACTIVE for production");
        }

        boolean uniquenessAmbiguous =
                "MULTIPLE_RESOLVED_BY_PRIORITY".equals(nested(runtime, "workflow").get("match"))
                        || "MULTIPLE_RESOLVED_BY_PRIORITY".equals(nested(runtime, "liveRuleSet").get("match"))
                        || "MULTIPLE_RESOLVED_BY_PRIORITY".equals(nested(runtime, "scorecard").get("match"))
                        || "NO_MATCH".equals(nested(runtime, "workflow").get("match"))
                        || "NO_MATCH".equals(nested(runtime, "liveRuleSet").get("match"))
                        || "NO_MATCH".equals(nested(runtime, "scorecard").get("match"));

        out.put("productConfigMatchesRuntime", mismatches.isEmpty());
        out.put("mismatchKeys", keys.stream().distinct().toList());
        out.put("messages", mismatches);
        out.put("ambiguous", !mismatches.isEmpty() || uniquenessAmbiguous);
        out.put("uniqueness", Map.of(
                "workflow", nested(runtime, "workflow").get("match"),
                "liveRuleSet", nested(runtime, "liveRuleSet").get("match"),
                "scorecard", nested(runtime, "scorecard").get("match")));
        return out;
    }

    private Map<String, Object> workflowResolution(WorkflowConfig chosen, List<WorkflowConfig> peers) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceOfTruth", "Runtime Workflow");
        m.put("label", "Runtime Workflow");
        m.put("resolver", "catalog list only — application pin required to consume a workflow");
        m.put("prioritySemantics", "no application default; Category pin is the new-app authority");
        if (chosen == null) {
            m.put("match", "NO_MATCH");
            m.put("id", null);
            m.put("resolvedByPriority", false);
            m.put("competitors", List.of());
            return m;
        }
        boolean multi = peers.size() > 1;
        m.put("match", multi ? "MULTIPLE_RESOLVED_BY_PRIORITY" : "EXACTLY_ONE");
        m.put("resolvedByPriority", multi);
        m.put("id", chosen.getId() == null ? null : chosen.getId().toString());
        m.put("name", chosen.getName());
        m.put("version", chosen.getVersion());
        m.put("active", chosen.isActive());
        m.put("borrowerType", chosen.getBorrowerType());
        m.put("loanProduct", chosen.getLoanProduct());
        m.put("intakeSegment", chosen.getIntakeSegment());
        m.put("lmsProductCode", chosen.getLmsProductCode());
        m.put("competitors", peers.stream().limit(8).map(w -> Map.of(
                "id", w.getId() == null ? "" : w.getId().toString(),
                "name", w.getName() == null ? "" : w.getName(),
                "version", w.getVersion())).toList());
        return m;
    }

    private Map<String, Object> ruleResolution(UnderwritingRuleSet top, List<UnderwritingRuleSet> all) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceOfTruth", "Runtime Rule Set");
        m.put("label", "Runtime Rule Set");
        m.put("resolver", "UnderwritingRuleSetRepository orderByPriorityDesc (UnderwritingRuleEngine)");
        m.put("prioritySemantics", "all matching active sets evaluated; top priority shown for Product Config alignment");
        if (top == null) {
            m.put("match", "NO_MATCH");
            m.put("id", null);
            m.put("resolvedByPriority", false);
            m.put("competitors", List.of());
            return m;
        }
        boolean multi = all.size() > 1;
        m.put("match", multi ? "MULTIPLE_RESOLVED_BY_PRIORITY" : "EXACTLY_ONE");
        m.put("resolvedByPriority", multi);
        m.put("id", top.getId() == null ? null : top.getId().toString());
        m.put("name", top.getName());
        m.put("priority", top.getPriority());
        m.put("active", top.isActive());
        m.put("borrowerType", top.getBorrowerType());
        m.put("loanProduct", top.getLoanProduct());
        m.put("competitors", all.stream().limit(8).map(r -> Map.of(
                "id", r.getId() == null ? "" : r.getId().toString(),
                "name", r.getName() == null ? "" : r.getName(),
                "priority", r.getPriority())).toList());
        return m;
    }

    private Map<String, Object> scorecardResolution(UnderwritingScorecard chosen, List<UnderwritingScorecard> all) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceOfTruth", "Runtime Scorecard");
        m.put("label", "Runtime Scorecard");
        m.put("resolver", "ScorecardPolicyEngine: active+priority then first amount-scope match");
        if (chosen == null) {
            m.put("match", "NO_MATCH");
            m.put("id", null);
            m.put("runtimeActive", false);
            m.put("resolvedByPriority", false);
            m.put("competitors", List.of());
            return m;
        }
        String status = chosen.getStatus() == null ? "ACTIVE" : chosen.getStatus();
        boolean runtimeActive = chosen.isActive()
                && ScorecardGovernanceStatuses.ACTIVE.equalsIgnoreCase(status);
        boolean multi = all.size() > 1;
        m.put("match", multi ? "MULTIPLE_RESOLVED_BY_PRIORITY" : "EXACTLY_ONE");
        m.put("resolvedByPriority", multi);
        m.put("id", chosen.getId() == null ? null : chosen.getId().toString());
        m.put("name", chosen.getName());
        m.put("version", chosen.getVersion());
        m.put("priority", chosen.getPriority());
        m.put("active", chosen.isActive());
        m.put("status", status);
        m.put("runtimeActive", runtimeActive);
        m.put("competitors", all.stream().limit(8).map(s -> Map.of(
                "id", s.getId() == null ? "" : s.getId().toString(),
                "name", s.getName() == null ? "" : s.getName(),
                "priority", s.getPriority(),
                "version", s.getVersion(),
                "status", s.getStatus() == null ? "" : s.getStatus())).toList());
        return m;
    }

    private static UnderwritingScorecard firstScopeMatch(List<UnderwritingScorecard> cands, BigDecimal amount) {
        for (UnderwritingScorecard c : cands) {
            if (c.getMinAmount() != null && (amount == null || amount.compareTo(c.getMinAmount()) < 0)) {
                continue;
            }
            if (c.getMaxAmount() != null && (amount == null || amount.compareTo(c.getMaxAmount()) > 0)) {
                continue;
            }
            return c;
        }
        return null;
    }

    private static LoanApplication syntheticApp(
            String borrowerType, String loanProduct, String intakeSegment, BigDecimal amount) {
        LoanApplication app = new LoanApplication();
        if (borrowerType != null) {
            try {
                app.setBorrowerType(BorrowerType.valueOf(borrowerType.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                /* leave null */
            }
        }
        app.setLoanProduct(loanProduct);
        try {
            app.setIntakeSegment(IntakeSegment.valueOf(
                    (intakeSegment == null ? "BORROWER" : intakeSegment).trim().toUpperCase(Locale.ROOT)));
        } catch (Exception e) {
            app.setIntakeSegment(IntakeSegment.BORROWER);
        }
        app.setRequestedAmount(amount);
        return app;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> root, String key) {
        Object v = root.get(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static UUID idOf(Map<String, Object> m) {
        Object id = m.get("id");
        if (id == null) return null;
        try {
            return UUID.fromString(String.valueOf(id));
        } catch (Exception e) {
            return null;
        }
    }
}
