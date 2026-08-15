package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LOS-LIVE-READINESS-1 / POLICY-STUDIO-GATE3 — thin read-model: workflow/KYC step →
 * production-ready CanonicalParameterRegistry ids only.
 * <p>
 * Studio-only / non-production parameters are listed separately and do NOT satisfy
 * Product Configuration production readiness.
 */
public final class WorkflowParameterProvidesCatalog {

    private WorkflowParameterProvidesCatalog() {}

    /** Proven production-grade provides (filtered at read time by GACAT productionReady). */
    private static final Map<String, List<String>> STEP_PROVIDES = Map.ofEntries(
            Map.entry("BUREAU_PULL", List.of(
                    "bureau.score",
                    "bureau.max_dpd_12m",
                    "bureau.max_dpd_24m",
                    "bureau.recent_inquiries_90d",
                    "bureau.live_unsecured_loan_count",
                    "bureau.total_live_exposure",
                    "bureau.secured_live_exposure",
                    "bureau.unsecured_live_exposure",
                    "bureau.total_monthly_obligation",
                    "bureau.settled_account_count",
                    "bureau.written_off_account_count",
                    "bureau.tradeline.payment_history",
                    "bureau.tradeline.account_open_date",
                    "bureau.inquiry")),
            Map.entry("GST_ANALYSIS", List.of(
                    "gst.turnover.trailing_12m")),
            Map.entry("GSTIN_VERIFY", List.of(
                    // Identity verification only — does NOT provide GST turnover
            )),
            Map.entry("ITR_RETURN_FORMS", List.of(
                    // ITR primitives not yet seeded as first-class registry params for UW
            )),
            Map.entry("BANK_PENNY_DROP", List.of(
                    // Penny-drop verifies account — does NOT provide ADB / BSA metrics
            )),
            Map.entry("PAN_VERIFY", List.of("kyc.pan.verified", "kyc.quality")),
            Map.entry("AADHAAR_OTP", List.of("kyc.quality")),
            Map.entry("FACE_MATCH", List.of("kyc.quality")),
            Map.entry("LIVENESS", List.of("kyc.quality")),
            Map.entry("VIDEO_KYC", List.of("kyc.quality")),
            Map.entry("CKYC_DOWNLOAD", List.of("kyc.quality")),
            Map.entry("AML_SCREENING", List.of("kyc.quality")),
            Map.entry("CIN_MCA21", List.of("kyc.quality", "kyc.cin.verified")),
            Map.entry("UDYAM_VERIFY", List.of("kyc.quality")),
            Map.entry("MOBILE_OTP", List.of("kyc.quality")),
            Map.entry("EMAIL_OTP", List.of("kyc.quality"))
    );

    /** Studio / Policy-Test helpers — NOT counted as production workflow provides. */
    private static final Map<String, List<String>> STEP_STUDIO_ONLY = Map.of(
            "BUREAU_PULL", List.of(
                    "bureau.max_dpd_6m",
                    "bureau.status_ntc",
                    "bureau.inquiries.current_month",
                    "bureau.cc_overdue_amount",
                    "bureau.overdue.age_months",
                    "bureau.overdue.amount",
                    "bureau.credit_after_overdue.exists",
                    "bureau.credit_after_overdue.clean_history_months",
                    "bureau.accounts.writeoff_non_cc",
                    "bureau.accounts.cc_writeoff")
    );

    private static final Map<String, List<String>> INTEGRATION_PROVIDES = Map.of(
            "ACCOUNT_AGGREGATOR", List.of(
                    "banking.avg_daily_balance_3m",
                    "banking.emi_bounce_count_3m",
                    "bank.transaction.amount",
                    "bank.transaction.date",
                    "bank.account.closing_balance",
                    "bank.transaction.credit_debit",
                    "bank.transaction.classification"),
            "BANK_STATEMENT_DOCUMENT", List.of(
                    "banking.avg_daily_balance_3m",
                    "banking.emi_bounce_count_3m",
                    "bank.account.closing_balance")
    );

    /** Studio-only banking metrics — Policy Test / BRE authoring, not auto Product Config. */
    private static final Map<String, List<String>> INTEGRATION_STUDIO_ONLY = Map.of(
            "ACCOUNT_AGGREGATOR", List.of(
                    "banking.monthly_credits_3m",
                    "banking.cheque_return_count_3m",
                    "banking.settlement.count_monthly_avg_3m",
                    "banking.settlement.avg_daily_3m",
                    "banking.transaction_count.average_monthly_3m",
                    "banking.inward_return.ratio_3m"),
            "BANK_STATEMENT_DOCUMENT", List.of(
                    "banking.monthly_credits_3m",
                    "banking.cheque_return_count_3m")
    );

    public static Map<String, Object> catalogueView() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : STEP_PROVIDES.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("step", e.getKey());
            List<String> prod = filterProductionReady(e.getValue());
            row.put("providesParameterIds", prod);
            row.put("providesCount", prod.size());
            row.put("studioOnlyParameterIds", STEP_STUDIO_ONLY.getOrDefault(e.getKey(), List.of()));
            row.put("note", noteForStep(e.getKey()));
            steps.add(row);
        }
        out.put("workflowSteps", steps);
        List<Map<String, Object>> integrations = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : INTEGRATION_PROVIDES.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("integration", e.getKey());
            row.put("providesParameterIds", filterProductionReady(e.getValue()));
            row.put("studioOnlyParameterIds", INTEGRATION_STUDIO_ONLY.getOrDefault(e.getKey(), List.of()));
            row.put("boundToWorkflowStep", false);
            row.put("note", "Available as LOS integration/document path — not a Workflow executor step unless configured separately.");
            integrations.add(row);
        }
        out.put("integrationsNotBoundToWorkflowSteps", integrations);
        out.put("allowCanonicalAuthority", false);
        out.put("readModelOnly", true);
        out.put("gate3", "production provides filtered by ParameterExecutabilitySupport.productionReady");
        return out;
    }

    public static Set<String> parametersProvidedByWorkflowSteps(List<Map<String, Object>> steps) {
        Set<String> ids = new LinkedHashSet<>();
        if (steps == null) return ids;
        for (Map<String, Object> step : steps) {
            String type = stepType(step);
            if (type == null) continue;
            ids.addAll(filterProductionReady(STEP_PROVIDES.getOrDefault(type, List.of())));
        }
        return ids;
    }

    /** Production-ready ids only — never promote studio overlays via workflow claims. */
    static List<String> filterProductionReady(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            Map<String, Object> exec = ParameterExecutabilitySupport.evaluate(id);
            if (Boolean.TRUE.equals(exec.get("productionReady"))) {
                out.add(id);
                continue;
            }
            var def = registry.findById(id);
            if (def.isPresent() && "RAW".equalsIgnoreCase(def.get().type())
                    && def.get().capability() != null
                    && def.get().capability().sourceAvailable()
                    && def.get().capability().normalized()) {
                out.add(id);
            }
        }
        return out;
    }

    public static Set<String> parametersProvidedByWorkflow(
            List<Map<String, Object>> steps, boolean bureauEnabled, boolean autoBureauAfterKyc) {
        Set<String> ids = parametersProvidedByWorkflowSteps(steps);
        boolean hasBureauStep = steps != null && steps.stream()
                .anyMatch(s -> "BUREAU_PULL".equals(stepType(s)));
        if ((bureauEnabled || autoBureauAfterKyc || hasBureauStep) && !hasBureauStep) {
            ids.addAll(filterProductionReady(STEP_PROVIDES.getOrDefault("BUREAU_PULL", List.of())));
        }
        if (hasBureauStep) {
            ids.addAll(filterProductionReady(STEP_PROVIDES.getOrDefault("BUREAU_PULL", List.of())));
        }
        return ids;
    }

    public static boolean hasGstAnalysis(List<Map<String, Object>> steps) {
        return steps != null && steps.stream().anyMatch(s -> "GST_ANALYSIS".equals(stepType(s)));
    }

    public static boolean hasBankingAcquisition(List<Map<String, Object>> steps) {
        if (steps == null) return false;
        return steps.stream().anyMatch(s -> {
            String t = stepType(s);
            return "ACCOUNT_AGGREGATOR".equals(t)
                    || "BANK_STATEMENT_ANALYSIS".equals(t)
                    || "BSA".equals(t);
        });
    }

    public static String stepType(Map<String, Object> step) {
        if (step == null) return null;
        Object raw = step.get("step");
        if (raw == null) raw = step.get("stepType");
        if (raw == null) raw = step.get("type");
        if (raw == null) raw = step.get("kycStepType");
        if (raw == null) return null;
        return String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
    }

    /**
     * DP-1 inverse lookup: which workflow steps / integrations claim this parameter
     * (production lists + studio-only — visibility only; does not change production filtering).
     */
    public static Map<String, Object> lookupForParameter(String parameterId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (parameterId == null || parameterId.isBlank()) {
            out.put("workflowAvailable", false);
            out.put("integrationAvailable", false);
            out.put("productionSteps", List.of());
            out.put("studioOnlySteps", List.of());
            out.put("integrations", List.of());
            return out;
        }
        String id = parameterId.trim();
        List<String> productionSteps = new ArrayList<>();
        List<String> studioOnlySteps = new ArrayList<>();
        List<String> integrations = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : STEP_PROVIDES.entrySet()) {
            if (e.getValue().contains(id)) {
                productionSteps.add(e.getKey());
            }
        }
        for (Map.Entry<String, List<String>> e : STEP_STUDIO_ONLY.entrySet()) {
            if (e.getValue().contains(id)) {
                studioOnlySteps.add(e.getKey());
            }
        }
        for (Map.Entry<String, List<String>> e : INTEGRATION_PROVIDES.entrySet()) {
            if (e.getValue().contains(id)) {
                integrations.add(e.getKey());
            }
        }
        for (Map.Entry<String, List<String>> e : INTEGRATION_STUDIO_ONLY.entrySet()) {
            if (e.getValue().contains(id) && !integrations.contains(e.getKey())) {
                integrations.add(e.getKey());
            }
        }
        out.put("workflowAvailable", !productionSteps.isEmpty() || !studioOnlySteps.isEmpty());
        out.put("integrationAvailable", !integrations.isEmpty());
        out.put("productionSteps", productionSteps);
        out.put("studioOnlySteps", studioOnlySteps);
        out.put("integrations", integrations);
        out.put("readModelOnly", true);
        return out;
    }

    private static String noteForStep(String step) {
        return switch (step) {
            case "GSTIN_VERIFY" -> "Verifies GSTIN identity only — does NOT satisfy gst.turnover.*";
            case "BANK_PENNY_DROP" -> "Verifies bank account only — does NOT satisfy Bank Statement Analysis / ADB";
            case "BUREAU_PULL" -> "Provides production bureau metrics only; studio helpers (max_dpd_6m, writeoff_non_cc) listed under studioOnly";
            case "PAN_VERIFY" -> "Provides kyc.pan.verified + kyc.quality";
            case "GST_ANALYSIS" -> "Karza GST PDF analysis — populates GST turnover when complete.";
            case "ITR_RETURN_FORMS" -> "ITR pull exists; registry UW params for ITR not fully seeded.";
            default -> "Proven production parameter ids only";
        };
    }
}
