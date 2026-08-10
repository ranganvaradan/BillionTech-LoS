package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.IntakeSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads co-applicant settings from workflow {@code intakeConfig.coApplicant}.
 * Always disabled for invoice-discounting products and ANCHOR intake.
 */
public final class CoApplicantWorkflowConfig {

    public static final String KEY = "coApplicant";

    private CoApplicantWorkflowConfig() {
    }

    public record Settings(
            boolean enabled,
            int minCoApplicants,
            int maxCoApplicants,
            boolean captureAtRmCreate,
            boolean notifyAllOnInvite,
            List<String> primaryKycSteps,
            List<String> coApplicantKycSteps,
            boolean requireAllEsignBeforeDisbursement,
            String underwritingParty
    ) {
        public static Settings disabled() {
            return new Settings(false, 0, 0, false, false,
                    List.of(), List.of(), true, "PRIMARY");
        }
    }

    public static Settings fromWorkflow(WorkflowConfig workflow, LoanApplication app) {
        if (workflow == null) {
            return Settings.disabled();
        }
        return fromIntakeConfig(workflow.getIntakeConfig(), app);
    }

    public static Settings fromIntakeConfig(Map<String, Object> intakeConfig, LoanApplication app) {
        if (app != null) {
            if (app.getIntakeSegment() == IntakeSegment.ANCHOR) {
                return Settings.disabled();
            }
            if (InvoiceDiscountingApplicationRules.isInvoiceDiscounting(app)) {
                return Settings.disabled();
            }
        }
        if (intakeConfig == null || intakeConfig.isEmpty()) {
            return Settings.disabled();
        }
        Object raw = intakeConfig.get(KEY);
        if (!(raw instanceof Map<?, ?> map)) {
            return Settings.disabled();
        }
        boolean enabled = bool(map.get("enabled"), false);
        if (!enabled) {
            return Settings.disabled();
        }
        int min = intVal(map.get("minCoApplicants"), 0);
        int max = intVal(map.get("maxCoApplicants"), 3);
        if (max < 1) {
            max = 1;
        }
        if (min < 0) {
            min = 0;
        }
        if (min > max) {
            min = max;
        }
        return new Settings(
                true,
                min,
                max,
                bool(map.get("captureAtRmCreate"), true),
                bool(map.get("notifyAllOnInvite"), true),
                stringList(map.get("primaryKycSteps")),
                stringList(map.get("coApplicantKycSteps")),
                bool(map.get("requireAllEsignBeforeDisbursement"), true),
                stringVal(map.get("underwritingParty"), "PRIMARY")
        );
    }

    /** Force-off when saving workflows for invoice-discounting products. */
    public static Map<String, Object> forceDisabledInIntakeConfig(Map<String, Object> intakeConfig) {
        if (intakeConfig == null) {
            return null;
        }
        Map<String, Object> copy = new java.util.LinkedHashMap<>(intakeConfig);
        Map<String, Object> co = new java.util.LinkedHashMap<>();
        co.put("enabled", false);
        co.put("minCoApplicants", 0);
        co.put("maxCoApplicants", 0);
        co.put("captureAtRmCreate", false);
        co.put("notifyAllOnInvite", false);
        co.put("primaryKycSteps", List.of());
        co.put("coApplicantKycSteps", List.of());
        co.put("requireAllEsignBeforeDisbursement", true);
        co.put("underwritingParty", "PRIMARY");
        copy.put(KEY, co);
        return copy;
    }

    private static boolean bool(Object v, boolean def) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(String.valueOf(v));
        }
        return def;
    }

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static String stringVal(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s.toUpperCase(Locale.ROOT);
    }

    private static List<String> stringList(Object v) {
        if (!(v instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) {
                out.add(s.toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }
}
