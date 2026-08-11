package com.los.core.service.readiness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LOS-LIVE-READINESS-1 — thin read-model: workflow/KYC step → CanonicalParameterRegistry ids.
 * Proven mappings only — not execution logic.
 */
public final class WorkflowParameterProvidesCatalog {

    private WorkflowParameterProvidesCatalog() {}

    private static final Map<String, List<String>> STEP_PROVIDES = Map.ofEntries(
            Map.entry("BUREAU_PULL", List.of(
                    "bureau.score",
                    "bureau.max_dpd_6m",
                    "bureau.inquiries.current_month",
                    "bureau.live_unsecured_loan_count",
                    "bureau.cc_overdue_amount",
                    "bureau.overdue.age_months",
                    "bureau.overdue.amount",
                    "bureau.credit_after_overdue.exists",
                    "bureau.credit_after_overdue.clean_history_months",
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
            Map.entry("PAN_VERIFY", List.of("kyc.quality")),
            Map.entry("AADHAAR_OTP", List.of("kyc.quality")),
            Map.entry("FACE_MATCH", List.of("kyc.quality")),
            Map.entry("LIVENESS", List.of("kyc.quality")),
            Map.entry("VIDEO_KYC", List.of("kyc.quality")),
            Map.entry("CKYC_DOWNLOAD", List.of("kyc.quality")),
            Map.entry("AML_SCREENING", List.of("kyc.quality")),
            Map.entry("CIN_MCA21", List.of("kyc.quality")),
            Map.entry("UDYAM_VERIFY", List.of("kyc.quality")),
            Map.entry("MOBILE_OTP", List.of("kyc.quality")),
            Map.entry("EMAIL_OTP", List.of("kyc.quality"))
    );

    /** Proven integration acquisition paths that are NOT workflow executor steps. */
    private static final Map<String, List<String>> INTEGRATION_PROVIDES = Map.of(
            "ACCOUNT_AGGREGATOR", List.of(
                    "banking.avg_daily_balance_3m",
                    "banking.monthly_credits_3m",
                    "banking.cheque_return_count_3m",
                    "banking.settlement.count_monthly_avg_3m",
                    "banking.settlement.avg_daily_3m",
                    "banking.transaction_count.average_monthly_3m",
                    "banking.inward_return.ratio_3m",
                    "bank.transaction.amount",
                    "bank.transaction.date",
                    "bank.account.closing_balance",
                    "bank.transaction.credit_debit",
                    "bank.transaction.classification"),
            "BANK_STATEMENT_DOCUMENT", List.of(
                    "banking.avg_daily_balance_3m",
                    "banking.monthly_credits_3m",
                    "banking.cheque_return_count_3m",
                    "bank.account.closing_balance")
    );

    public static Map<String, Object> catalogueView() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : STEP_PROVIDES.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("step", e.getKey());
            row.put("providesParameterIds", e.getValue());
            row.put("providesCount", e.getValue().size());
            row.put("note", noteForStep(e.getKey()));
            steps.add(row);
        }
        out.put("workflowSteps", steps);
        List<Map<String, Object>> integrations = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : INTEGRATION_PROVIDES.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("integration", e.getKey());
            row.put("providesParameterIds", e.getValue());
            row.put("boundToWorkflowStep", false);
            row.put("note", "Available as LOS integration/document path — not a Workflow executor step unless configured separately.");
            integrations.add(row);
        }
        out.put("integrationsNotBoundToWorkflowSteps", integrations);
        out.put("allowCanonicalAuthority", false);
        out.put("readModelOnly", true);
        return out;
    }

    public static Set<String> parametersProvidedByWorkflowSteps(List<Map<String, Object>> steps) {
        Set<String> ids = new LinkedHashSet<>();
        if (steps == null) return ids;
        for (Map<String, Object> step : steps) {
            String type = stepType(step);
            if (type == null) continue;
            List<String> provides = STEP_PROVIDES.getOrDefault(type, List.of());
            ids.addAll(provides);
            // Auto bureau after KYC is a flag, not a step — caller may add BUREAU_PULL separately
        }
        return ids;
    }

    public static Set<String> parametersProvidedByWorkflow(
            List<Map<String, Object>> steps, boolean bureauEnabled, boolean autoBureauAfterKyc) {
        Set<String> ids = parametersProvidedByWorkflowSteps(steps);
        boolean hasBureauStep = steps != null && steps.stream()
                .anyMatch(s -> "BUREAU_PULL".equals(stepType(s)));
        if ((bureauEnabled || autoBureauAfterKyc || hasBureauStep) && !hasBureauStep) {
            // Bureau pull is a separate FlowStepType even when not listed inside KYC steps JSON
            ids.addAll(STEP_PROVIDES.getOrDefault("BUREAU_PULL", List.of()));
        }
        if (hasBureauStep) {
            ids.addAll(STEP_PROVIDES.getOrDefault("BUREAU_PULL", List.of()));
        }
        return ids;
    }

    public static boolean hasGstAnalysis(List<Map<String, Object>> steps) {
        return steps != null && steps.stream().anyMatch(s -> "GST_ANALYSIS".equals(stepType(s)));
    }

    public static boolean hasBankingAcquisition(List<Map<String, Object>> steps) {
        // No proven BANK_STATEMENT / AA workflow step today
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
        if (raw == null) return null;
        return String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
    }

    private static String noteForStep(String step) {
        return switch (step) {
            case "GSTIN_VERIFY" -> "GSTIN identity verify only — does not populate GST turnover.";
            case "BANK_PENNY_DROP" -> "Account verification only — does not populate ADB or statement metrics.";
            case "ITR_RETURN_FORMS" -> "ITR pull exists; registry UW params for ITR not fully seeded.";
            case "BUREAU_PULL" -> "Bureau pull FlowStepType / KYC step.";
            case "GST_ANALYSIS" -> "Karza GST PDF analysis — populates GST turnover when complete.";
            default -> "KYC / verification contribution.";
        };
    }
}
