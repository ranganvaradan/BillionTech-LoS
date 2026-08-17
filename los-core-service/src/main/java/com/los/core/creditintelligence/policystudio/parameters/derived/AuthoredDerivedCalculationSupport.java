package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.execution.AuthoredDerivedProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges Spring-managed derived-calc definitions into static rule/operand presenters
 * so session cards and inventory share the same calculation truth as the execution spine.
 *
 * <p>"Definition exists" is not "valid executable calculation". Overlay only clears
 * {@code calculationRequired} / marks {@code calculationDefined} when the expression
 * would pass {@link AuthoredDerivedProducer} intent validation for the exact canonical id.
 */
@Component
@RequiredArgsConstructor
public class AuthoredDerivedCalculationSupport {

    private static volatile AuthoredDerivedCalculationSupport INSTANCE;

    private final DerivedCalculationDefinitionService definitionService;

    @PostConstruct
    public void register() {
        INSTANCE = this;
    }

    @PreDestroy
    public void unregister() {
        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }

    public static void overlayOperand(Map<String, Object> face) {
        AuthoredDerivedCalculationSupport inst = INSTANCE;
        if (inst == null || face == null) return;
        inst.applyOverlay(face);
    }

    public static boolean isBuiltInCodeDefinition(Map<String, Object> expr) {
        return AuthoredDerivedProducer.isBuiltInCodeExpression(expr);
    }

    /**
     * Latest non-retired definition row for UI / READY — includes BUILT_IN_CODE
     * (not spine-executable as a formula).
     */
    public static Optional<Map<String, Object>> latestDefinitionPresent(String canonicalParameterId) {
        AuthoredDerivedCalculationSupport inst = INSTANCE;
        if (inst == null || canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return Optional.empty();
        }
        return inst.definitionService.latestFor(canonicalParameterId.trim(), null)
                .filter(d -> d.getStatus() == null
                        || !DerivedCalculationDefinitionService.STATUS_RETIRED.equalsIgnoreCase(d.getStatus()))
                .map(AuthoredDerivedCalculationSupport::definitionMeta);
    }

    /**
     * Lender-facing calculation narrative. BUILT_IN_CODE returns description without
     * requiring a spine formula.
     */
    public static Optional<String> latestExecutableHow(String canonicalParameterId) {
        AuthoredDerivedCalculationSupport inst = INSTANCE;
        if (inst == null || canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return Optional.empty();
        }
        return inst.definitionService.latestFor(canonicalParameterId.trim(), null)
                .filter(d -> isHowEligible(canonicalParameterId.trim(), d))
                .map(CiGacatDerivedCalculationDefinition::getDescription)
                .filter(s -> s != null && !s.isBlank());
    }

    public static Optional<List<String>> latestExecutableDependencies(String canonicalParameterId) {
        AuthoredDerivedCalculationSupport inst = INSTANCE;
        if (inst == null || canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return Optional.empty();
        }
        return inst.definitionService.latestFor(canonicalParameterId.trim(), null)
                .filter(d -> isHowEligible(canonicalParameterId.trim(), d))
                .map(CiGacatDerivedCalculationDefinition::getDependencyIds)
                .filter(deps -> deps != null && !deps.isEmpty());
    }

    private static boolean isHowEligible(String canonicalId, CiGacatDerivedCalculationDefinition d) {
        if (d == null) return false;
        if (isBuiltIn(d)) return true;
        return AuthoredDerivedProducer.isSpineExecutableDefinition(canonicalId, d.getExpressionJson());
    }

    private static boolean isBuiltIn(CiGacatDerivedCalculationDefinition d) {
        if (d == null) return false;
        if (DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE
                .equalsIgnoreCase(d.getCalculationType() == null ? "" : d.getCalculationType())) {
            return true;
        }
        return isBuiltInCodeDefinition(d.getExpressionJson());
    }

    private static Map<String, Object> definitionMeta(CiGacatDerivedCalculationDefinition d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("definitionId", d.getId() == null ? null : d.getId().toString());
        m.put("versionNo", d.getVersionNo());
        m.put("status", d.getStatus());
        String type = d.getCalculationType();
        if (type == null || type.isBlank()) {
            type = isBuiltInCodeDefinition(d.getExpressionJson())
                    ? DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE
                    : DerivedCalculationDefinitionService.CALCULATION_TYPE_AUTHORED_EXPRESSION;
        }
        m.put("calculationType", type);
        Object executor = null;
        if (d.getExpressionJson() != null) {
            executor = d.getExpressionJson().get("executor");
        }
        if (executor == null && d.getMetadata() != null) {
            executor = d.getMetadata().get("executionAuthority");
        }
        m.put("executor", executor);
        m.put("description", d.getDescription());
        return m;
    }

    private void applyOverlay(Map<String, Object> face) {
        Object pid = face.get("parameterId");
        if (pid == null || String.valueOf(pid).isBlank()) return;
        String canonicalId = String.valueOf(pid).trim();
        Optional<CiGacatDerivedCalculationDefinition> authored =
                definitionService.latestFor(canonicalId, null);
        if (authored.isEmpty()) {
            face.putIfAbsent("calculationDefined", false);
            return;
        }
        CiGacatDerivedCalculationDefinition d = authored.get();
        face.put("calculationDefinitionStatus", d.getStatus());
        face.put("calculationDefinitionId", d.getId() == null ? null : d.getId().toString());
        face.put("calculationDefinitionVersion", d.getVersionNo());

        Map<String, Object> expr = d.getExpressionJson();
        boolean builtIn = isBuiltIn(d);
        boolean spineExecutable =
                AuthoredDerivedProducer.isSpineExecutableDefinition(canonicalId, expr);

        if (!spineExecutable && !builtIn) {
            // Stored row may exist (wrong op) — do NOT suppress lender calculation CTA
            face.put("calculationDefined", false);
            face.put("calculationDefinedButNotExecutable", true);
            face.put("calculationRequired", true);
            face.put("needsConfiguration", true);
            face.put("executionReadinessCause", "CALCULATION_REQUIRED");
            face.put("message",
                    "A stored calculation definition exists but is not executable for this parameter — "
                            + "confirm or replace the calculation.");
            face.put("policyTestReady", false);
            return;
        }

        face.put("calculationDefined", true);
        face.put("calculationDefinedButNotExecutable", false);
        face.put("calculationRequired", false);
        if (Boolean.TRUE.equals(face.get("needsConfiguration"))
                && "CALCULATION_REQUIRED".equals(String.valueOf(face.get("executionReadinessCause")))) {
            face.put("needsConfiguration", false);
            face.remove("executionReadinessCause");
            face.remove("message");
            String avail = String.valueOf(face.getOrDefault("availability", ""));
            if ("NEEDS_CONFIGURATION".equalsIgnoreCase(avail)
                    || "NEEDS_CONFIG".equalsIgnoreCase(avail)) {
                face.put("availability", "DERIVED");
                face.put("availabilityLabel", "Derived automatically");
            }
        }
        if (d.getDescription() != null && !d.getDescription().isBlank()) {
            String how = d.getDescription().trim();
            if (d.getDependencyIds() != null && !d.getDependencyIds().isEmpty()) {
                List<String> inputs = humanizeInputs(d.getDependencyIds(), d.getExpressionJson());
                if (!inputs.isEmpty()) {
                    how = how + "\n\nInputs:\n- " + String.join("\n- ", inputs);
                }
            }
            face.put("howCalculated", how);
            face.put("calculationInputs", humanizeInputs(d.getDependencyIds(), d.getExpressionJson()));
        } else if (d.getDependencyIds() != null && !d.getDependencyIds().isEmpty()) {
            face.put("calculationInputs", humanizeInputs(d.getDependencyIds(), d.getExpressionJson()));
        }
        String st = d.getStatus() == null ? "" : d.getStatus().toUpperCase(Locale.ROOT);
        if (DerivedCalculationDefinitionService.STATUS_TESTED.equals(st)
                || DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY.equals(st)
                || DerivedCalculationDefinitionService.STATUS_DEFINED.equals(st)) {
            face.put("policyTestReady", true);
        }
        if (DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY.equals(st)) {
            face.put("calculationProductionReady", true);
        }
    }

    public static List<String> humanizeInputs(List<String> deps, Map<String, Object> expr) {
        List<String> out = new ArrayList<>();
        boolean paymentHistory = deps.stream().anyMatch(d ->
                d != null && d.toLowerCase(Locale.ROOT).contains("payment_history"));
        if (paymentHistory) {
            out.add("Bureau payment history");
            Object matchField = expr == null ? null : expr.get("matchField");
            if (matchField != null && "dpd".equalsIgnoreCase(String.valueOf(matchField))) {
                out.add("Days past due");
            }
            Object dateField = expr == null ? null : expr.get("dateField");
            if (dateField != null && "month".equalsIgnoreCase(String.valueOf(dateField))) {
                out.add("Reporting month");
            }
        } else {
            out.addAll(deps);
        }
        return out;
    }
}
