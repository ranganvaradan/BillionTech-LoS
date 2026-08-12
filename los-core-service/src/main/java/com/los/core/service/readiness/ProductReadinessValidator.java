package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.service.underwriting.ScorecardGovernanceStatuses;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * LOS-LIVE-READINESS-1 — read-only Product × Workflow × Policy × Scorecard readiness.
 */
@Service
public class ProductReadinessValidator {

    public static final String AVAILABLE_AUTOMATICALLY = "AVAILABLE_AUTOMATICALLY";
    public static final String DERIVABLE = "DERIVABLE";
    public static final String MANUAL = "MANUAL";
    public static final String UNRESOLVED = "UNRESOLVED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private final PolicyRequiredParameterExtractor extractor;

    public ProductReadinessValidator(PolicyRequiredParameterExtractor extractor) {
        this.extractor = extractor;
    }

    private CanonicalParameterRegistry registry() {
        return PolicyStudioConvergencePresenter.registry();
    }

    public Map<String, Object> validate(
            String borrowerType,
            String loanProduct,
            WorkflowConfig workflow,
            UnderwritingRuleSet liveRuleSet,
            UnderwritingScorecard scorecard,
            Map<String, Object> studioRequired,
            String policyDocumentId,
            String policyVersionLabel) {

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("productionAuthority", "LIVE_UW_PATH");
        out.put("resolutionOrder", List.of(
                "Application → Product dimensions",
                "Workflow",
                "Live Rule Set",
                "Live Scorecard",
                "Studio Policy publication metadata only"));

        Map<String, Object> refs = new LinkedHashMap<>();
        refs.put("borrowerType", borrowerType);
        refs.put("loanProduct", loanProduct);
        if (workflow != null) {
            refs.put("workflowId", workflow.getId() == null ? null : workflow.getId().toString());
            refs.put("workflowName", workflow.getName());
            refs.put("workflowVersion", workflow.getVersion());
        }
        if (liveRuleSet != null) {
            refs.put("liveRuleSetId", liveRuleSet.getId() == null ? null : liveRuleSet.getId().toString());
            refs.put("liveRuleSetName", liveRuleSet.getName());
        }
        if (scorecard != null) {
            refs.put("scorecardId", scorecard.getId() == null ? null : scorecard.getId().toString());
            refs.put("scorecardName", scorecard.getName());
            refs.put("scorecardPriority", scorecard.getPriority());
            refs.put("scorecardVersion", scorecard.getVersion());
            refs.put("scorecardStatus", scorecard.getStatus());
            refs.put("scorecardSourceOfTruth", "Runtime Scorecard");
        }
        if (workflow != null) {
            refs.put("workflowSourceOfTruth", "Runtime Workflow");
        }
        if (liveRuleSet != null) {
            refs.put("liveRuleSetSourceOfTruth", "Runtime Rule Set");
        }
        refs.put("policyDocumentId", policyDocumentId);
        refs.put("policyVersion", policyVersionLabel);
        if (policyDocumentId != null) {
            refs.put("policyStudioSourceOfTruth", "Policy Studio Policy — Governance only / Shadow");
            refs.put("policyStudioNotProductionAuthority", true);
        }
        out.put("references", refs);

        boolean scopeOk = scopeCompatible(borrowerType, loanProduct, workflow, liveRuleSet, scorecard);
        out.put("scopeCompatible", scopeOk);

        Set<String> provided = workflow == null
                ? Set.of()
                : WorkflowParameterProvidesCatalog.parametersProvidedByWorkflow(
                        workflow.getSteps(), workflow.isBureauEnabled(),
                        workflow.isAutoPullBureauAfterKycSuccess());

        Map<String, Object> requiredBundle = mergeRequired(liveRuleSet, scorecard, studioRequired);
        @SuppressWarnings("unchecked")
        List<String> requiredIds = (List<String>) requiredBundle.getOrDefault("requiredParameterIds", List.of());
        List<Map<String, Object>> classified = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        int autoMissing = 0;
        int manualCount = 0;
        int unresolved = 0;

        for (String pid : requiredIds) {
            Map<String, Object> row = classify(pid, provided, workflow);
            classified.add(row);
            String c = String.valueOf(row.get("classification"));
            if (UNAVAILABLE.equals(c) || UNRESOLVED.equals(c)) {
                autoMissing++;
                gaps.add(String.valueOf(row.get("gap")));
            } else if (MANUAL.equals(c)) {
                manualCount++;
            }
            if (UNRESOLVED.equals(c)) unresolved++;
        }
        out.put("requiredParameters", classified);
        out.put("requiredCount", requiredIds.size());
        out.put("workflowProvidesParameterIds", provided);
        out.put("workflowSuppliesRequiredAutomaticData", autoMissing == 0 && unresolved == 0);
        out.put("manualParamsCapturePath", manualCapture(manualCount, classified));
        out.put("policyEvaluationPositionSafe", true); // underwrite after KYC/bureau gates in existing flow
        out.put("manualReviewPathExists", true); // completeManualUnderwritingDecision
        out.put("rejectPathExists", true);
        out.put("approveCamPathExists", true);
        boolean scorecardOk = scorecardCompatible(borrowerType, loanProduct, scorecard);
        boolean scorecardRuntimeActive = scorecardRuntimeActive(scorecard);
        out.put("scorecardCompatible", scorecardOk);
        out.put("scorecardRuntimeActive", scorecardRuntimeActive);
        out.put("versionReferencesResolvable",
                (workflow == null || workflow.getId() != null)
                        && (liveRuleSet == null || liveRuleSet.getId() != null)
                        && (scorecard == null || scorecard.getId() != null));

        if (!scopeOk) {
            gaps.add(scopeGap(borrowerType, loanProduct, workflow, liveRuleSet, scorecard));
        }
        if (!scorecardOk) {
            gaps.add(scorecard == null
                    ? "No Scorecard selected — hard-rule-only products may still underwrite"
                    : "Scorecard scope mismatch: scorecard="
                    + scorecard.getBorrowerType() + "/" + scorecard.getLoanProduct()
                    + " vs product=" + borrowerType + "/" + loanProduct);
        }
        if (scorecard != null && !scorecardRuntimeActive) {
            gaps.add("SCORECARD_NOT_RUNTIME_ACTIVE: status="
                    + (scorecard.getStatus() == null ? "null" : scorecard.getStatus())
                    + " — only ACTIVE scorecard versions satisfy production Product Configuration");
        }

        String manualPath = String.valueOf(out.get("manualParamsCapturePath"));
        boolean ready = Boolean.TRUE.equals(out.get("scopeCompatible"))
                && Boolean.TRUE.equals(out.get("workflowSuppliesRequiredAutomaticData"))
                && !"NO".equals(manualPath)
                && !"NEEDS CONFIGURATION".equals(manualPath)
                && scorecardOk
                && scorecardRuntimeActive
                && Boolean.TRUE.equals(out.get("versionReferencesResolvable"))
                && workflow != null
                && liveRuleSet != null;

        // Without Live Rule Set, not runtime-ready (Studio is governance only)
        if (liveRuleSet == null) {
            ready = false;
            gaps.add("No Live Rule Set selected — Policy Studio is governance/shadow only");
        }
        if (workflow == null) {
            ready = false;
            gaps.add("No Workflow selected");
        }
        if ("NEEDS CONFIGURATION".equals(manualPath)) {
            ready = false;
            gaps.add("Manual parameter(s) lack a proven runtime capture path");
        }

        out.put("ready", ready);
        out.put("status", ready ? "READY" : "NOT READY");
        out.put("gaps", gaps.stream().filter(Objects::nonNull).distinct().toList());
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("scopeCompatible", yn(scopeOk));
        checks.put("workflowSuppliesRequiredAutomaticData", yn(Boolean.TRUE.equals(out.get("workflowSuppliesRequiredAutomaticData"))));
        checks.put("manualParamsHaveCapturePath", manualPath);
        checks.put("policyEvaluationPositionSafe", "YES");
        checks.put("manualReviewPathExists", "YES");
        checks.put("rejectPathExists", "YES");
        checks.put("approveCamPathExists", "YES");
        checks.put("scorecardCompatible", yn(scorecardOk));
        checks.put("scorecardRuntimeActive", yn(scorecardRuntimeActive));
        checks.put("versionReferencesResolvable", yn(Boolean.TRUE.equals(out.get("versionReferencesResolvable"))));
        out.put("checks", checks);
        return out;
    }

    private Map<String, Object> mergeRequired(
            UnderwritingRuleSet liveRuleSet,
            UnderwritingScorecard scorecard,
            Map<String, Object> studioRequired) {
        Map<String, Object> live = extractor.fromLiveRuleSet(liveRuleSet);
        Map<String, Object> sc = extractor.fromLiveScorecard(scorecard);
        Set<String> ids = new java.util.LinkedHashSet<>();
        @SuppressWarnings("unchecked")
        List<String> a = (List<String>) live.getOrDefault("requiredParameterIds", List.of());
        @SuppressWarnings("unchecked")
        List<String> b = (List<String>) sc.getOrDefault("requiredParameterIds", List.of());
        ids.addAll(a);
        ids.addAll(b);
        if (studioRequired != null) {
            @SuppressWarnings("unchecked")
            List<String> c = (List<String>) studioRequired.getOrDefault("requiredParameterIds", List.of());
            ids.addAll(c);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requiredParameterIds", new ArrayList<>(ids));
        return out;
    }

    private Map<String, Object> classify(String parameterId, Set<String> provided, WorkflowConfig workflow) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("parameterId", parameterId);
        Map<String, Object> exec = ParameterExecutabilitySupport.evaluate(parameterId);
        ParameterExecutabilitySupport.stampOnto(row, exec);
        var defOpt = registry().findById(parameterId);
        if (defOpt.isEmpty() && !Boolean.TRUE.equals(exec.get("policyTestReady"))) {
            row.put("classification", UNRESOLVED);
            row.put("gap", "Parameter not in CanonicalParameterRegistry: " + parameterId);
            return row;
        }
        if (defOpt.isEmpty()) {
            // Overlay: Policy-Test only — never AVAILABLE_AUTOMATICALLY for Product Config
            row.put("classification", UNAVAILABLE);
            row.put("gap", parameterId + " is Policy-Test/studio only — not productionReady");
            row.put("note", "Gate-3: Product Config uses same executability as ParameterExecutabilitySupport");
            return row;
        }
        CanonicalParameterDefinition def = defOpt.get();
        row.put("businessName", def.businessName());
        row.put("source", def.evaluatedFrom());
        row.put("type", def.type());

        if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type())) {
            row.put("classification", MANUAL);
            row.put("readiness", "MANUAL_AUTHORISED");
            row.put("actor", "Credit Analyst / Relationship Manager");
            row.put("captureStage", "Application / CAM underwriting review");
            row.put("workflowPoint", "Pre-decision manual underwriting / CAM capture");
            row.put("gap", null);
            row.put("note", "Manual input — Application/CAM path; provenance MANUAL_AUTHORISED");
            return row;
        }

        // Gate-3: Product Config production readiness requires productionReady=true
        if (!Boolean.TRUE.equals(exec.get("productionReady"))) {
            row.put("classification", UNAVAILABLE);
            row.put("readiness", String.valueOf(exec.get("executionState")));
            row.put("gap", def.businessName()
                    + " is not productionReady (executionState=" + exec.get("executionState")
                    + ") — Policy Test may still evaluate it");
            return row;
        }

        if (provided.contains(parameterId)) {
            row.put("classification", AVAILABLE_AUTOMATICALLY);
            row.put("gap", null);
            return row;
        }
        if ("obligation.ratio".equals(parameterId) || "application.proposed_edi".equals(parameterId)) {
            row.put("classification", DERIVABLE);
            row.put("gap", null);
            row.put("note", "Derived from application income/obligation/EDI fields via CreditControl");
            return row;
        }
        // Banking params: not supplied by typical KYC workflow
        if (parameterId.startsWith("banking.") || parameterId.startsWith("bank.")) {
            row.put("classification", UNAVAILABLE);
            row.put("readiness", "UNAVAILABLE");
            row.put("gap", def.businessName()
                    + " cannot be populated before underwriting — selected workflow has no Bank Statement Analysis / AA acquisition step");
            return row;
        }
        if (parameterId.startsWith("gst.")) {
            boolean gst = workflow != null && WorkflowParameterProvidesCatalog.hasGstAnalysis(workflow.getSteps());
            if (!gst) {
                row.put("classification", UNAVAILABLE);
                row.put("gap", def.businessName() + " cannot be populated — workflow missing GST_ANALYSIS");
                return row;
            }
        }
        if (parameterId.startsWith("bureau.")) {
            row.put("classification", UNAVAILABLE);
            row.put("gap", def.businessName() + " — bureau parameters not provided by workflow (enable bureau pull)");
            return row;
        }
        row.put("classification", UNAVAILABLE);
        row.put("gap", def.businessName() + " not supplied by selected workflow steps");
        return row;
    }

    private static String manualCapture(int manualCount, List<Map<String, Object>> classified) {
        if (manualCount == 0) return "YES";
        // Known MANUAL params have Application/CAM capture; unknown MANUAL without registry → already UNRESOLVED
        boolean anyUnknown = classified.stream().anyMatch(r ->
                MANUAL.equals(String.valueOf(r.get("classification")))
                        && r.get("actor") == null);
        return anyUnknown ? "NEEDS CONFIGURATION" : "YES";
    }

    private boolean scopeCompatible(
            String borrowerType, String loanProduct,
            WorkflowConfig workflow, UnderwritingRuleSet rules, UnderwritingScorecard scorecard) {
        if (borrowerType == null || loanProduct == null) return false;
        boolean ok = true;
        if (workflow != null) {
            ok &= eq(borrowerType, workflow.getBorrowerType());
            ok &= productMatches(loanProduct, workflow.getLoanProduct());
        }
        if (rules != null) {
            ok &= eq(borrowerType, rules.getBorrowerType());
            ok &= productMatches(loanProduct, rules.getLoanProduct());
        }
        if (scorecard != null) {
            ok &= eq(borrowerType, scorecard.getBorrowerType());
            ok &= productMatches(loanProduct, scorecard.getLoanProduct());
        }
        return ok;
    }

    private boolean scorecardCompatible(String borrowerType, String loanProduct, UnderwritingScorecard sc) {
        if (sc == null) return false;
        return eq(borrowerType, sc.getBorrowerType())
                && productMatches(loanProduct, sc.getLoanProduct())
                && sc.isActive();
    }

    private static boolean scorecardRuntimeActive(UnderwritingScorecard sc) {
        if (sc == null) return false;
        String status = sc.getStatus() == null ? ScorecardGovernanceStatuses.ACTIVE : sc.getStatus();
        return sc.isActive() && ScorecardGovernanceStatuses.ACTIVE.equalsIgnoreCase(status);
    }

    private static String scopeGap(
            String borrowerType, String loanProduct,
            WorkflowConfig workflow, UnderwritingRuleSet rules, UnderwritingScorecard scorecard) {
        StringBuilder sb = new StringBuilder("Scope incompatible for ")
                .append(borrowerType).append("/").append(loanProduct).append(":");
        if (workflow != null && !(eq(borrowerType, workflow.getBorrowerType())
                && productMatches(loanProduct, workflow.getLoanProduct()))) {
            sb.append(" workflow=").append(workflow.getBorrowerType()).append("/")
                    .append(workflow.getLoanProduct());
        }
        if (rules != null && !(eq(borrowerType, rules.getBorrowerType())
                && productMatches(loanProduct, rules.getLoanProduct()))) {
            sb.append(" liveRuleSet=").append(rules.getBorrowerType()).append("/")
                    .append(rules.getLoanProduct());
        }
        if (scorecard != null && !(eq(borrowerType, scorecard.getBorrowerType())
                && productMatches(loanProduct, scorecard.getLoanProduct()))) {
            sb.append(" scorecard=").append(scorecard.getBorrowerType()).append("/")
                    .append(scorecard.getLoanProduct());
        }
        return sb.toString();
    }

    private static boolean eq(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Exact product match after normalize. Do NOT use substring contains —
     * TERM_LOAN must not silently match BUSINESS_TERM_LOAN (routing ambiguity).
     */
    private static boolean productMatches(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.replace(' ', '_').toUpperCase(Locale.ROOT).trim();
        String y = b.replace(' ', '_').toUpperCase(Locale.ROOT).trim();
        return x.equals(y);
    }

    private static String yn(boolean v) {
        return v ? "YES" : "NO";
    }
}
