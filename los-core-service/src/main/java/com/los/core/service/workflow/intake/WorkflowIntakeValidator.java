package com.los.core.service.workflow.intake;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.DocumentRepository;
import com.los.core.service.loan.ApplicantIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enforces workflow intake rules at application submit.
 * Geography ({@code allowedStates}) is checked whenever configured.
 * Other field/document rules apply when {@code intakeConfig.policy} is WORKFLOW_DRIVEN.
 */
@Component
@RequiredArgsConstructor
public class WorkflowIntakeValidator {

    private static final Pattern PAN_RE = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$", Pattern.CASE_INSENSITIVE);

    private final DocumentRepository documentRepository;

    public void validateAtSubmit(LoanApplication app, WorkflowConfig workflow) {
        if (workflow == null) {
            return;
        }
        Map<String, Object> intakeConfig = workflow.getIntakeConfig() != null
                ? workflow.getIntakeConfig()
                : Map.of();
        Map<String, Object> personal = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        Map<String, Object> business = app.getBusinessInfo() != null ? app.getBusinessInfo() : Map.of();

        // Soft geography check — applies whenever allowedStates is configured (including LEGACY).
        validateAllowedStates(intakeConfig, personal, business);

        if (!KycStepIntakeCatalog.isWorkflowDriven(intakeConfig)) {
            return;
        }
        List<Map<String, Object>> steps = workflow.getSteps() != null ? workflow.getSteps() : List.of();

        validateConfiguredFields(app, steps, intakeConfig, personal, business);
        validateMandatoryGroups(steps, intakeConfig, personal, business);
        validatePersonalFields(intakeConfig, personal, app.getBorrowerType());
        validateCustomFields(intakeConfig, customFieldMap(personal));
        validateAge(intakeConfig, personal);
        validateTenure(intakeConfig, app.getTenureMonths());
        validateRequiredDocuments(app.getId(), steps, intakeConfig);
    }

    /**
     * When {@code allowedStates} is empty / missing / ALL, no restriction (current default).
     * When a non-empty list is configured, personal or business state must match one of them.
     */
    private void validateAllowedStates(
            Map<String, Object> intakeConfig,
            Map<String, Object> personal,
            Map<String, Object> business) {
        List<String> allowed = allowedStatesFromConfig(intakeConfig);
        if (allowed.isEmpty()) {
            return;
        }
        String personalState = stringValue(personal.get("state"));
        String businessState = stringValue(business.get("state"));
        String state = !personalState.isBlank() ? personalState : businessState;
        if (state.isBlank()) {
            return;
        }
        boolean ok = allowed.stream().anyMatch(a -> a.equalsIgnoreCase(state));
        if (!ok) {
            throw new BusinessRuleException(
                    "Selected state is not configured for this workflow: " + state,
                    "STATE_NOT_ALLOWED_FOR_WORKFLOW",
                    "SUBMIT_APPLICATION",
                    Map.of("state", state, "allowedStates", allowed));
        }
    }

    private static List<String> allowedStatesFromConfig(Map<String, Object> intakeConfig) {
        if (intakeConfig == null) {
            return List.of();
        }
        Object raw = intakeConfig.get("allowedStates");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        if (out.size() == 1 && "ALL".equalsIgnoreCase(out.get(0))) {
            return List.of();
        }
        return out;
    }

    private void validateConfiguredFields(
            LoanApplication app,
            List<Map<String, Object>> steps,
            Map<String, Object> intakeConfig,
            Map<String, Object> personal,
            Map<String, Object> business) {
        Set<String> groupedSteps = groupedStepNames(intakeConfig);
        for (Map<String, Object> step : steps) {
            String stepName = stringValue(step.get("step"));
            if (stepName.isBlank()) {
                continue;
            }
            if (!collectAtIntake(step)) {
                continue;
            }
            if (groupedSteps.contains(stepName)) {
                continue;
            }
            if (!fieldRequiredAtIntake(step)) {
                continue;
            }
            KycStepIntakeCatalog.StepIntakeMeta meta = KycStepIntakeCatalog.metaForStep(stepName);
            if (meta == null || meta.fieldKey() == null) {
                continue;
            }
            String value = resolveFieldValue(meta.fieldKey(), personal, business, app);
            if (value.isBlank()) {
                throw fieldError(meta.fieldKey(), "Required field missing for workflow step " + stepName);
            }
            if ("panNumber".equals(meta.fieldKey()) && !PAN_RE.matcher(value).matches()) {
                throw fieldError("panNumber", "Enter a valid 10-character PAN (e.g. ABCDE1234F).");
            }
        }
    }

    private void validateMandatoryGroups(
            List<Map<String, Object>> steps,
            Map<String, Object> intakeConfig,
            Map<String, Object> personal,
            Map<String, Object> business) {
        Object raw = intakeConfig.get("mandatoryFieldGroups");
        if (!(raw instanceof List<?> groups) || groups.isEmpty()) {
            return;
        }
        Set<String> configuredSteps = steps.stream()
                .map(s -> stringValue(s.get("step")).toUpperCase())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        for (Object item : groups) {
            if (!(item instanceof Map<?, ?> group)) {
                continue;
            }
            String logic = stringValue(group.get("logic"));
            if (!"ANY".equalsIgnoreCase(logic)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<String> members = group.get("steps") instanceof List<?> list
                    ? list.stream().map(s -> stringValue(s).toUpperCase()).filter(s -> !s.isBlank()).toList()
                    : List.of();
            if (members.isEmpty()) {
                continue;
            }
            boolean anyPresent = false;
            for (String member : members) {
                if (!configuredSteps.contains(member)) {
                    continue;
                }
                KycStepIntakeCatalog.StepIntakeMeta meta = KycStepIntakeCatalog.metaForStep(member);
                if (meta == null || meta.fieldKey() == null) {
                    continue;
                }
                String value = resolveFieldValue(meta.fieldKey(), personal, business, null);
                if (!value.isBlank()) {
                    anyPresent = true;
                    break;
                }
            }
            if (!anyPresent) {
                String label = stringValue(group.get("label"));
                String msg = label.isBlank()
                        ? "At least one identity document is required (configured OR group)."
                        : label + " — provide at least one of the configured options.";
                throw new BusinessRuleException(msg, "INTAKE_MANDATORY_GROUP", "SUBMIT_APPLICATION",
                        Map.of("group", label));
            }
        }
    }

    private void validatePersonalFields(Map<String, Object> intakeConfig, Map<String, Object> personal, BorrowerType borrowerType) {
        if (borrowerType != BorrowerType.INDIVIDUAL) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> personalFields = intakeConfig.get("personalFields") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        validatePersonalField(personalFields, personal, "dateOfBirth", "Date of birth");
        validatePersonalField(personalFields, personal, "gender", "Gender");
        validateCodedPersonalField(intakeConfig, personalFields, personal, "occupation", "Occupation", "occupationRules");
        validateCodedPersonalField(intakeConfig, personalFields, personal, "loanPurpose", "Loan purpose", "loanPurposeRules");
    }

    private void validateCustomFields(Map<String, Object> intakeConfig, Map<String, Object> customFields) {
        Object raw = intakeConfig.get("customFields");
        if (!(raw instanceof List<?> defs) || defs.isEmpty()) {
            return;
        }
        for (Object item : defs) {
            if (!(item instanceof Map<?, ?> field)) {
                continue;
            }
            String key = stringValue(field.get("key"));
            String label = stringValue(field.get("label"));
            String type = stringValue(field.get("type"));
            boolean required = boolValue(field.get("required"));
            Object rawValue = key.isBlank() ? null : customFields.get(key);
            String value = stringValue(rawValue);
            if (required) {
                if ("BOOLEAN".equalsIgnoreCase(type)) {
                    if (!(rawValue instanceof Boolean)) {
                        throw fieldError(key, (label.isBlank() ? key : label) + " is required.");
                    }
                } else if (value.isBlank()) {
                    throw fieldError(key, (label.isBlank() ? key : label) + " is required.");
                }
            }
            if ("NUMBER".equalsIgnoreCase(type) && !value.isBlank()) {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    throw fieldError(key, (label.isBlank() ? key : label) + " must be a valid number.");
                }
            }
        }
    }

    private void validateCodedPersonalField(
            Map<String, Object> intakeConfig,
            Map<String, Object> personalFields,
            Map<String, Object> personal,
            String key,
            String label,
            String rulesKey) {
        if (!(personalFields.get(key) instanceof Map<?, ?> cfg)) {
            return;
        }
        boolean collect = boolValue(cfg.get("collect"));
        boolean required = boolValue(cfg.get("required"));
        if (!collect) {
            return;
        }
        String value = stringValue(personal.get(key));
        if (required && value.isBlank()) {
            throw fieldError(key, label + " is required.");
        }
        if (value.isBlank()) {
            return;
        }
        List<IntakeOptionCatalog.CodedOption> options = "occupationRules".equals(rulesKey)
                ? IntakeOptionCatalog.occupationOptionsFromIntakeConfig(intakeConfig)
                : IntakeOptionCatalog.loanPurposeOptionsFromIntakeConfig(intakeConfig);
        boolean ok = options.stream().anyMatch(o -> o.value().equals(value));
        if (!ok) {
            throw fieldError(key, "Select a valid " + label.toLowerCase(Locale.ROOT) + " option.");
        }
    }

    private static void validatePersonalField(
            Map<String, Object> personalFields,
            Map<String, Object> personal,
            String key,
            String label) {
        if (!(personalFields.get(key) instanceof Map<?, ?> cfg)) {
            return;
        }
        boolean collect = boolValue(cfg.get("collect"));
        boolean required = boolValue(cfg.get("required"));
        if (!collect || !required) {
            return;
        }
        String value = stringValue(personal.get(key));
        if (value.isBlank()) {
            throw fieldError(key, label + " is required.");
        }
        if ("gender".equals(key) && cfg.get("allowedValues") instanceof List<?> allowed && !allowed.isEmpty()) {
            boolean ok = allowed.stream().anyMatch(v -> value.equalsIgnoreCase(stringValue(v)));
            if (!ok) {
                throw fieldError(key, "Select a valid gender option.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> customFieldMap(Map<String, Object> personal) {
        Object raw = personal.get("customFields");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private void validateAge(Map<String, Object> intakeConfig, Map<String, Object> personal) {
        if (!(intakeConfig.get("ageRules") instanceof Map<?, ?> ageRules)) {
            return;
        }
        if (!boolValue(ageRules.get("enabled"))) {
            return;
        }
        String dobStr = stringValue(personal.get("dateOfBirth"));
        if (dobStr.isBlank()) {
            throw fieldError("dateOfBirth", "Date of birth is required for age validation.");
        }
        LocalDate dob;
        try {
            dob = LocalDate.parse(dobStr);
        } catch (DateTimeParseException e) {
            throw fieldError("dateOfBirth", "Enter a valid date of birth.");
        }
        int age = Period.between(dob, LocalDate.now()).getYears();
        int minAge = intValue(ageRules.get("minAge"), 0);
        int maxAge = intValue(ageRules.get("maxAge"), 150);
        if (minAge > 0 && age < minAge) {
            throw fieldError("dateOfBirth", "Applicant must be at least " + minAge + " years old.");
        }
        if (maxAge > 0 && age > maxAge) {
            throw fieldError("dateOfBirth", "Applicant must be at most " + maxAge + " years old.");
        }
    }

    private void validateTenure(Map<String, Object> intakeConfig, Integer tenureMonths) {
        if (!(intakeConfig.get("tenureRules") instanceof Map<?, ?> tenureRules)) {
            return;
        }
        if (tenureMonths == null || tenureMonths <= 0) {
            throw new BusinessRuleException(
                    "Tenure is required.",
                    "INTAKE_TENURE_REQUIRED",
                    "SUBMIT_APPLICATION",
                    Map.of("field", "tenureMonths"));
        }
        String inputMode = stringValue(tenureRules.get("inputMode"));
        if ("dropdown".equalsIgnoreCase(inputMode) && tenureRules.get("options") instanceof List<?> options) {
            boolean match = options.stream().anyMatch(o -> {
                if (!(o instanceof Map<?, ?> opt)) {
                    return false;
                }
                String value = stringValue(opt.get("value"));
                return value.equals(String.valueOf(tenureMonths));
            });
            if (!match) {
                throw new BusinessRuleException(
                        "Select a valid tenure from the allowed options.",
                        "INTAKE_TENURE_INVALID",
                        "SUBMIT_APPLICATION",
                        Map.of("field", "tenureMonths"));
            }
        } else {
            int min = intValue(tenureRules.get("min"), 0);
            int max = intValue(tenureRules.get("max"), 0);
            if (min > 0 && tenureMonths < min) {
                throw new BusinessRuleException(
                        "Tenure must be at least " + min + ".",
                        "INTAKE_TENURE_MIN",
                        "SUBMIT_APPLICATION",
                        Map.of("field", "tenureMonths", "min", String.valueOf(min)));
            }
            if (max > 0 && tenureMonths > max) {
                throw new BusinessRuleException(
                        "Tenure must be at most " + max + ".",
                        "INTAKE_TENURE_MAX",
                        "SUBMIT_APPLICATION",
                        Map.of("field", "tenureMonths", "max", String.valueOf(max)));
            }
        }
    }

    private void validateRequiredDocuments(UUID applicationId, List<Map<String, Object>> steps, Map<String, Object> intakeConfig) {
        Set<String> uploaded = documentRepository.findByApplicationIdAndIsLatestTrueOrderByCreatedAtDesc(applicationId)
                .stream()
                .map(d -> d.getDocumentType() != null ? d.getDocumentType().toUpperCase() : "")
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        Set<String> required = new LinkedHashSet<>();
        for (Map<String, Object> step : steps) {
            if (!collectAtIntake(step)) {
                continue;
            }
            collectRequiredDocumentsFromStep(step, required);
        }
        Object standalone = intakeConfig.get("standaloneDocuments");
        if (standalone instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> doc && boolValue(doc.get("required"))) {
                    String type = stringValue(doc.get("documentType")).toUpperCase();
                    if (!type.isBlank()) {
                        required.add(type);
                    }
                }
            }
        }
        for (String docType : required) {
            if (!uploaded.contains(docType.toUpperCase())) {
                throw new BusinessRuleException(
                        "Required document not uploaded: " + docType,
                        "INTAKE_DOCUMENT_REQUIRED",
                        "SUBMIT_APPLICATION",
                        Map.of("documentType", docType));
            }
        }
    }

    private static void collectRequiredDocumentsFromStep(Map<String, Object> step, Set<String> required) {
        if (step.get("documentsRequired") instanceof List<?> docs) {
            for (Object item : docs) {
                if (item instanceof Map<?, ?> doc && boolValue(doc.get("required"))) {
                    String type = stringValue(doc.get("documentType")).toUpperCase();
                    if (!type.isBlank()) {
                        required.add(type);
                    }
                }
            }
            return;
        }
        if (boolValue(step.get("documentRequired"))) {
            KycStepIntakeCatalog.StepIntakeMeta meta = KycStepIntakeCatalog.metaForStep(stringValue(step.get("step")));
            if (meta != null) {
                meta.defaultDocumentTypes().forEach(t -> required.add(t.toUpperCase()));
            }
        }
    }

    private static boolean collectAtIntake(Map<String, Object> step) {
        Object explicit = step.get("collectAtIntake");
        if (explicit != null) {
            return boolValue(explicit);
        }
        return KycStepIntakeCatalog.metaForStep(stringValue(step.get("step"))) != null;
    }

    private static boolean fieldRequiredAtIntake(Map<String, Object> step) {
        Object explicit = step.get("fieldRequiredAtIntake");
        if (explicit != null) {
            return boolValue(explicit);
        }
        return boolValue(step.getOrDefault("mandatory", true));
    }

    private static String resolveFieldValue(
            String fieldKey,
            Map<String, Object> personal,
            Map<String, Object> business,
            LoanApplication app) {
        return switch (fieldKey) {
            case "panNumber" -> app != null ? ApplicantIdentityResolver.resolvePanNumber(app) : stringValue(personal.get("panNumber"));
            case "aadhaar" -> firstNonBlank(
                    personal.get("aadhaarNumber"),
                    personal.get("aadhaarLast4"),
                    personal.get("aadhaar"));
            case "voterId" -> firstNonBlank(personal.get("voterId"), personal.get("epicNo"));
            case "dlNumber" -> firstNonBlank(personal.get("dlNumber"), personal.get("dlNo"));
            case "gstin" -> firstNonBlank(business.get("gstin"), personal.get("gstin"));
            case "cin" -> firstNonBlank(business.get("cin"), personal.get("cin"));
            case "udyam" -> firstNonBlank(business.get("udyam"), personal.get("udyam"));
            case "bankAccountNumber" -> firstNonBlank(personal.get("bankAccountNumber"), business.get("bankAccountNumber"));
            default -> stringValue(personal.get(fieldKey));
        };
    }

    private static Set<String> groupedStepNames(Map<String, Object> intakeConfig) {
        if (intakeConfig == null) {
            return Set.of();
        }
        Object raw = intakeConfig.get("mandatoryFieldGroups");
        if (!(raw instanceof List<?> groups)) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (Object item : groups) {
            if (!(item instanceof Map<?, ?> group)) {
                continue;
            }
            if (group.get("steps") instanceof List<?> steps) {
                steps.forEach(s -> {
                    String name = stringValue(s).toUpperCase();
                    if (!name.isBlank()) {
                        out.add(name);
                    }
                });
            }
        }
        return out;
    }

    private static BusinessRuleException fieldError(String field, String message) {
        return new BusinessRuleException(message, "INTAKE_FIELD_REQUIRED", "SUBMIT_APPLICATION", Map.of("field", field));
    }

    private static String stringValue(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            String s = stringValue(v);
            if (!s.isBlank()) {
                return s;
            }
        }
        return "";
    }

    private static boolean boolValue(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(stringValue(o));
    }

    private static int intValue(Object o, int defaultValue) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(stringValue(o));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
