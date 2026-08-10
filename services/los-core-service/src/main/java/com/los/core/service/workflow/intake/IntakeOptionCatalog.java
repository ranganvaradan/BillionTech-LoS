package com.los.core.service.workflow.intake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default occupation and loan-purpose option catalogs (value + label) for intake validation.
 * Scoring for these codes is configured only on underwriting scorecards.
 */
public final class IntakeOptionCatalog {

    public record CodedOption(String value, String label) {
    }

    private IntakeOptionCatalog() {
    }

    public static List<CodedOption> defaultOccupationOptions() {
        return List.of(
                new CodedOption("OTHER", "None / Other"),
                new CodedOption("SELF_EMPLOYED_BUSINESS", "Self Employed / Business"),
                new CodedOption("SELF_EMPLOYED_PROFESSIONAL", "Self Employed professional"),
                new CodedOption("SALARIED_PRIVATE", "Salaried — private sector"),
                new CodedOption("SALARIED_GOVERNMENT", "Salaried — government"));
    }

    public static List<CodedOption> defaultLoanPurposeOptions() {
        return List.of(
                new CodedOption("OTHER", "Others"),
                new CodedOption("SIBLING_MARRIAGE", "Sibling's Marriage"),
                new CodedOption("PURCHASE_DURABLES", "Purchase of Durables"),
                new CodedOption("BUSINESS_PURPOSE", "Business Purpose"),
                new CodedOption("OWN_MARRIAGE", "Own Marriage"),
                new CodedOption("EDUCATION", "Education"),
                new CodedOption("VEHICLE_PURCHASE", "Vehicle Purchase"),
                new CodedOption("HOUSE_REPAIR", "House Repair"),
                new CodedOption("DEBT_CONSOLIDATION", "Debt Consolidation"));
    }

    public static List<Map<String, Object>> defaultOccupationOptionsMaps() {
        return toMaps(defaultOccupationOptions());
    }

    public static List<Map<String, Object>> defaultLoanPurposeOptionsMaps() {
        return toMaps(defaultLoanPurposeOptions());
    }

    private static List<Map<String, Object>> toMaps(List<CodedOption> options) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CodedOption o : options) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", o.value());
            m.put("label", o.label());
            out.add(m);
        }
        return out;
    }

    public static List<CodedOption> occupationOptionsFromIntakeConfig(Map<String, Object> intakeConfig) {
        List<CodedOption> configured = codedOptionsFromRules(intakeConfig, "occupationRules");
        return configured.isEmpty() ? defaultOccupationOptions() : configured;
    }

    public static List<CodedOption> loanPurposeOptionsFromIntakeConfig(Map<String, Object> intakeConfig) {
        List<CodedOption> configured = codedOptionsFromRules(intakeConfig, "loanPurposeRules");
        return configured.isEmpty() ? defaultLoanPurposeOptions() : configured;
    }

    private static List<CodedOption> codedOptionsFromRules(Map<String, Object> intakeConfig, String rulesKey) {
        if (intakeConfig == null || intakeConfig.isEmpty()) {
            return List.of();
        }
        Object rulesRaw = intakeConfig.get(rulesKey);
        if (!(rulesRaw instanceof Map<?, ?> rules)) {
            return List.of();
        }
        Object optionsRaw = rules.get("options");
        if (!(optionsRaw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<CodedOption> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String value = stringValue(m.get("value"));
            if (value.isBlank()) {
                continue;
            }
            String label = stringValue(m.get("label"));
            if (label.isBlank()) {
                label = value;
            }
            out.add(new CodedOption(value, label));
        }
        return out;
    }

    public static String resolveOccupationCode(Map<String, Object> personal) {
        if (personal == null) {
            return "";
        }
        String code = stringValue(personal.get("occupation"));
        if (!code.isBlank()) {
            return code;
        }
        String legacy = stringValue(personal.get("occupationIndustry"));
        if (legacy.isBlank()) {
            return "";
        }
        for (CodedOption o : defaultOccupationOptions()) {
            if (o.label().equalsIgnoreCase(legacy.trim())) {
                return o.value();
            }
        }
        return "OTHER";
    }

    public static String resolveLoanPurposeCode(Map<String, Object> personal) {
        if (personal == null) {
            return "";
        }
        String code = stringValue(personal.get("loanPurpose"));
        if (!code.isBlank()) {
            return code;
        }
        String legacy = stringValue(personal.get("purpose"));
        if (legacy.isBlank()) {
            return "";
        }
        for (CodedOption o : defaultLoanPurposeOptions()) {
            if (o.label().equalsIgnoreCase(legacy.trim())) {
                return o.value();
            }
        }
        return "OTHER";
    }

    private static String stringValue(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
