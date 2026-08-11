package com.los.core.service.readiness;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.model.entity.AssignmentRuleSet;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.AssignmentRuleSetRepository;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * LOS-LIVE-READINESS-1 — thin Product Configuration compose over existing tables.
 * No new persistence / engine / router.
 */
@Service
@RequiredArgsConstructor
public class ProductConfigurationComposeService {

    private final WorkflowConfigRepository workflowConfigRepository;
    private final UnderwritingRuleSetRepository ruleSetRepository;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final AssignmentRuleSetRepository assignmentRuleSetRepository;
    private final ProductReadinessValidator readinessValidator;
    private final PolicyRequiredParameterExtractor parameterExtractor;
    private final CreditIntelligenceProperties properties;

    @Autowired(required = false)
    private PolicyStudioOrchestrator policyStudioOrchestrator;

    public Map<String, Object> options() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("productionAuthority", "LIVE_UW_PATH");
        out.put("products", distinctProducts());
        out.put("workflows", workflowConfigRepository.findAll().stream()
                .sorted(Comparator.comparing(WorkflowConfig::getName, Comparator.nullsLast(String::compareTo)))
                .map(this::workflowSummary)
                .toList());
        out.put("liveRuleSets", ruleSetRepository.findAll().stream()
                .sorted(Comparator.comparing(UnderwritingRuleSet::getName, Comparator.nullsLast(String::compareTo)))
                .map(this::ruleSummary)
                .toList());
        out.put("scorecards", scorecardRepository.findAll().stream()
                .sorted(Comparator.comparing(UnderwritingScorecard::getName, Comparator.nullsLast(String::compareTo)))
                .map(this::scorecardSummary)
                .toList());
        out.put("assignmentRuleSets", assignmentRuleSetRepository.findAll().stream()
                .map(this::assignmentSummary)
                .toList());
        out.put("goldenPreset", goldenPreset());
        out.put("resolutionOrder", List.of(
                "Application → Product dimensions",
                "Workflow",
                "Live Rule Set",
                "Live Scorecard",
                "Studio Policy publication metadata only"));
        return out;
    }

    public Map<String, Object> compose(Map<String, Object> body) {
        String borrowerType = str(body, "borrowerType");
        String loanProduct = str(body, "loanProduct");
        String intakeSegment = str(body, "intakeSegment");
        if (intakeSegment == null) intakeSegment = "BORROWER";

        UUID workflowId = uuid(body, "workflowId");
        UUID liveRuleSetId = uuid(body, "liveRuleSetId");
        UUID scorecardId = uuid(body, "scorecardId");
        UUID assignmentId = uuid(body, "assignmentRuleSetId");
        UUID policyDocumentId = uuid(body, "policyDocumentId");

        WorkflowConfig workflow = workflowId == null ? null
                : workflowConfigRepository.findById(workflowId).orElse(null);
        UnderwritingRuleSet rules = liveRuleSetId == null ? null
                : ruleSetRepository.findById(liveRuleSetId).orElse(null);
        UnderwritingScorecard scorecard = scorecardId == null ? null
                : scorecardRepository.findById(scorecardId).orElse(null);
        AssignmentRuleSet assignment = assignmentId == null ? null
                : assignmentRuleSetRepository.findById(assignmentId).orElse(null);

        if (borrowerType == null && workflow != null) borrowerType = workflow.getBorrowerType();
        if (loanProduct == null && workflow != null) loanProduct = workflow.getLoanProduct();

        Map<String, Object> studioRequired = null;
        String policyVersion = null;
        String policyName = null;
        if (policyDocumentId != null && policyStudioOrchestrator != null) {
            try {
                PolicyStudioSession session = policyStudioOrchestrator.requireSession(policyDocumentId);
                studioRequired = parameterExtractor.fromStudioSession(session);
                if (session.getDocument() != null) {
                    policyName = session.getDocument().getName();
                    policyVersion = "v" + session.getDocument().getDocumentVersion();
                }
            } catch (Exception e) {
                studioRequired = Map.of(
                        "requiredParameterIds", List.of(),
                        "error", "Policy session not available: " + e.getMessage());
            }
        }

        Map<String, Object> readiness = readinessValidator.validate(
                borrowerType, loanProduct, workflow, rules, scorecard,
                studioRequired,
                policyDocumentId == null ? null : policyDocumentId.toString(),
                policyVersion);

        Map<String, Object> compose = new LinkedHashMap<>();
        compose.put("borrowerType", borrowerType);
        compose.put("loanProduct", loanProduct);
        compose.put("intakeSegment", intakeSegment);
        compose.put("workflow", workflow == null ? null : workflowSummary(workflow));
        compose.put("liveRuleSet", rules == null ? null : ruleSummary(rules));
        compose.put("scorecard", scorecard == null ? null : scorecardSummary(scorecard));
        compose.put("assignmentRuleSet", assignment == null ? null : assignmentSummary(assignment));
        if (policyDocumentId != null) {
            Map<String, Object> pol = new LinkedHashMap<>();
            pol.put("documentId", policyDocumentId.toString());
            pol.put("policyName", policyName);
            pol.put("policyVersion", policyVersion);
            pol.put("authority", "METADATA_ONLY_NOT_PRODUCTION");
            compose.put("policyStudio", pol);
        } else {
            compose.put("policyStudio", null);
        }
        if (workflow != null) {
            compose.put("lms", Map.of(
                    "lmsProductCode", workflow.getLmsProductCode() == null ? "" : workflow.getLmsProductCode(),
                    "lmsTenureUnit", workflow.getLmsTenureUnit() == null ? "" : workflow.getLmsTenureUnit()));
        } else {
            compose.put("lms", null);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", cutoverFalse());
        out.put("compose", compose);
        out.put("studioRequiredParameters", studioRequired);
        out.put("readiness", readiness);
        out.put("status", readiness.get("status"));
        out.put("ready", readiness.get("ready"));
        return out;
    }

    /** Staging-safe golden composition using existing fixtures when present. */
    public Map<String, Object> goldenCompose() {
        Map<String, Object> preset = goldenPreset();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new LinkedHashMap<>((Map<String, Object>) preset.get("selection"));
        Map<String, Object> composed = compose(body);
        composed.put("golden", true);
        composed.put("goldenNote", preset.get("note"));
        return composed;
    }

    private Map<String, Object> goldenPreset() {
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("borrowerType", "COMPANY");
        selection.put("loanProduct", "TERM_LOAN");
        selection.put("intakeSegment", "BORROWER");

        Optional<WorkflowConfig> wf = workflowConfigRepository.findAll().stream()
                .filter(w -> "COMPANY".equalsIgnoreCase(w.getBorrowerType())
                        && "TERM_LOAN".equalsIgnoreCase(w.getLoanProduct())
                        && w.isActive())
                .max(Comparator.comparingInt(WorkflowConfig::getVersion));
        wf.ifPresent(w -> selection.put("workflowId", w.getId().toString()));

        ruleSetRepository.findAll().stream()
                .filter(r -> "COMPANY".equalsIgnoreCase(r.getBorrowerType())
                        && productMatches(r.getLoanProduct(), "TERM_LOAN")
                        && r.isActive())
                .max(Comparator.comparingInt(UnderwritingRuleSet::getPriority))
                .ifPresent(r -> selection.put("liveRuleSetId", r.getId().toString()));

        scorecardRepository.findAll().stream()
                .filter(s -> "COMPANY".equalsIgnoreCase(s.getBorrowerType())
                        && productMatches(s.getLoanProduct(), "TERM_LOAN")
                        && s.isActive())
                .max(Comparator.comparingInt(UnderwritingScorecard::getPriority))
                .ifPresent(s -> selection.put("scorecardId", s.getId().toString()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", "Company Term Loan (staging golden)");
        out.put("selection", selection);
        out.put("note", "Uses existing active Workflow / Live Rule Set / Scorecard for COMPANY + TERM_LOAN. "
                + "Readiness may be NOT READY when banking/GST parameters are required but workflow lacks those steps.");
        return out;
    }

    private List<Map<String, Object>> distinctProducts() {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WorkflowConfig w : workflowConfigRepository.findAll()) {
            String key = (w.getBorrowerType() + "|" + w.getLoanProduct() + "|"
                    + (w.getIntakeSegment() == null ? "BORROWER" : w.getIntakeSegment()))
                    .toUpperCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("borrowerType", w.getBorrowerType());
            row.put("loanProduct", w.getLoanProduct());
            row.put("intakeSegment", w.getIntakeSegment());
            row.put("sampleWorkflowId", w.getId() == null ? null : w.getId().toString());
            row.put("sampleWorkflowName", w.getName());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> workflowSummary(WorkflowConfig w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId() == null ? null : w.getId().toString());
        m.put("name", w.getName());
        m.put("borrowerType", w.getBorrowerType());
        m.put("loanProduct", w.getLoanProduct());
        m.put("intakeSegment", w.getIntakeSegment());
        m.put("version", w.getVersion());
        m.put("active", w.isActive());
        m.put("bureauEnabled", w.isBureauEnabled());
        m.put("autoPullBureauAfterKycSuccess", w.isAutoPullBureauAfterKycSuccess());
        m.put("stepCount", w.getSteps() == null ? 0 : w.getSteps().size());
        m.put("steps", w.getSteps() == null ? List.of() : w.getSteps().stream()
                .map(WorkflowParameterProvidesCatalog::stepType)
                .filter(s -> s != null)
                .toList());
        m.put("lmsProductCode", w.getLmsProductCode());
        m.put("providesParameterIds", WorkflowParameterProvidesCatalog.parametersProvidedByWorkflow(
                w.getSteps(), w.isBureauEnabled(), w.isAutoPullBureauAfterKycSuccess()));
        return m;
    }

    private Map<String, Object> ruleSummary(UnderwritingRuleSet r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId() == null ? null : r.getId().toString());
        m.put("name", r.getName());
        m.put("borrowerType", r.getBorrowerType());
        m.put("loanProduct", r.getLoanProduct());
        m.put("priority", r.getPriority());
        m.put("active", r.isActive());
        return m;
    }

    private Map<String, Object> scorecardSummary(UnderwritingScorecard s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId() == null ? null : s.getId().toString());
        m.put("name", s.getName());
        m.put("borrowerType", s.getBorrowerType());
        m.put("loanProduct", s.getLoanProduct());
        m.put("version", s.getVersion());
        m.put("priority", s.getPriority());
        m.put("active", s.isActive());
        return m;
    }

    private Map<String, Object> assignmentSummary(AssignmentRuleSet a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId() == null ? null : a.getId().toString());
        m.put("name", a.getName());
        m.put("borrowerType", a.getBorrowerType());
        m.put("loanProduct", a.getLoanProduct());
        m.put("active", a.isActive());
        return m;
    }

    /** Always false — readiness compose must never advertise production canonical authority. */
    private boolean cutoverFalse() {
        return false;
    }

    private static boolean productMatches(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.replace(' ', '_').toUpperCase(Locale.ROOT);
        String y = b.replace(' ', '_').toUpperCase(Locale.ROOT);
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    private static String str(Map<String, Object> body, String k) {
        if (body == null || body.get(k) == null) return null;
        String s = String.valueOf(body.get(k)).trim();
        return s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static UUID uuid(Map<String, Object> body, String k) {
        String s = str(body, k);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}
