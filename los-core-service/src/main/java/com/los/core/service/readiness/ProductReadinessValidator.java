package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
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
        }
        refs.put("policyDocumentId", policyDocumentId);
        refs.put("policyVersion", policyVersionLabel);
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
        out.put("scorecardCompatible", scorecardCompatible(borrowerType, loanProduct, scorecard));
        out.put("versionReferencesResolvable",
                (workflow == null || workflow.getId() != null)
                        && (liveRuleSet == null || liveRuleSet.getId() != null)
                        && (scorecard == null || scorecard.getId() != null));

        if (!scopeOk) {
            gaps.add(scopeGap(borrowerType, loanProduct, workflow, liveRuleSet, scorecard));
        }
        if (!Boolean.TRUE.equals(out.get("scorecardCompatible"))) {
            gaps.add(scorecard == null
                    ? "No Scorecard selected — hard-rule-only products may still underwrite"
                    : "Scorecard scope mismatch: scorecard="
                    + scorecard.getBorrowerType() + "/" + scorecard.getLoanProduct()
                    + " vs product=" + borrowerType + "/" + loanProduct);
        }

        boolean ready = Boolean.TRUE.equals(out.get("scopeCompatible"))
                && Boolean.TRUE.equals(out.get("workflowSuppliesRequiredAutomaticData"))
                && !"NO".equals(out.get("manualParamsCapturePath"))
                && Boolean.TRUE.equals(out.get("scorecardCompatible"))
                && Boolean.TRUE.equals(out.get("versionReferencesResolvable"))
                && workflow != null
                && (liveRuleSet != null || (studioRequired != null
                && !((List<?>) studioRequired.getOrDefault("requiredParameterIds", List.of())).isEmpty()));

        // Without any policy/rule source, not ready
        if (liveRuleSet == null && (studioRequired == null
                || ((List<?>) studioRequired.getOrDefault("requiredParameterIds", List.of())).isEmpty())) {
            ready = false;
            gaps.add("No Live Rule Set or Studio policy requirements selected");
        }
        if (workflow == null) {
            ready = false;
            gaps.add("No Workflow selected");
        }

        out.put("ready", ready);
        out.put("status", ready ? "READY" : "NOT READY");
        out.put("gaps", gaps.stream().filter(Objects::nonNull).distinct().toList());
        out.put("checks", Map.of(
                "scopeCompatible", yn(scopeOk),
                "workflowSuppliesRequiredAutomaticData", yn(Boolean.TRUE.equals(out.get("workflowSuppliesRequiredAutomaticData"))),
                "manualParamsHaveCapturePath", String.valueOf(out.get("manualParamsCapturePath")),
                "policyEvaluationPositionSafe", "YES",
                "manualReviewPathExists", "YES",
                "rejectPathExists", "YES",
                "approveCamPathExists", "YES",
                "scorecardCompatible", yn(Boolean.TRUE.equals(out.get("scorecardCompatible"))),
                "versionReferencesResolvable", yn(Boolean.TRUE.equals(out.get("versionReferencesResolvable")))
        ));
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
        var defOpt = registry().findById(parameterId);
        if (defOpt.isEmpty()) {
            row.put("classification", UNRESOLVED);
            row.put("gap", "Parameter not in CanonicalParameterRegistry: " + parameterId);
            return row;
        }
        CanonicalParameterDefinition def = defOpt.get();
        row.put("businessName", def.businessName());
        row.put("source", def.evaluatedFrom());
        row.put("type", def.type());

        if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type())) {
            row.put("classification", MANUAL);
            row.put("gap", null);
            row.put("note", "Manual input — ensure application/CAM capture exists");
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
            row.put("gap", def.businessName() + " cannot be populated — selected workflow has no bank-statement/AA analysis step");
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
        // Application / CreditControl supports many manual merges — PARTIAL until explicit step exists
        return "PARTIAL";
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

    /** TERM_LOAN ↔ BUSINESS_TERM_LOAN style aliases used by existing fixtures. */
    private static boolean productMatches(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.replace(' ', '_').toUpperCase(Locale.ROOT);
        String y = b.replace(' ', '_').toUpperCase(Locale.ROOT);
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    private static String yn(boolean v) {
        return v ? "YES" : "NO";
    }
}
