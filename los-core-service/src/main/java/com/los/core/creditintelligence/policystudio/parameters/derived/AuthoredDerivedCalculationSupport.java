package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.execution.AuthoredDerivedProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    void register() {
        INSTANCE = this;
    }

    @PreDestroy
    void unregister() {
        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }

    public static void overlayOperand(Map<String, Object> face) {
        AuthoredDerivedCalculationSupport inst = INSTANCE;
        if (inst == null || face == null) return;
        inst.applyOverlay(face);
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
        boolean spineExecutable =
                AuthoredDerivedProducer.isSpineExecutableDefinition(canonicalId, expr);

        if (!spineExecutable) {
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
            face.put("howCalculated", d.getDescription());
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
}
