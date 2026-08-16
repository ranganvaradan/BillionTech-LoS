package com.los.core.creditintelligence.policystudio.parameters.derived;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges Spring-managed derived-calc definitions into static rule/operand presenters
 * so session cards and inventory share the same "calculationDefined" truth.
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
        Optional<CiGacatDerivedCalculationDefinition> authored =
                definitionService.latestFor(String.valueOf(pid), null);
        if (authored.isEmpty()) {
            face.putIfAbsent("calculationDefined", false);
            return;
        }
        CiGacatDerivedCalculationDefinition d = authored.get();
        face.put("calculationDefined", true);
        face.put("calculationDefinitionStatus", d.getStatus());
        face.put("calculationDefinitionId", d.getId() == null ? null : d.getId().toString());
        face.put("calculationDefinitionVersion", d.getVersionNo());
        // Authored definition satisfies catalogue "derivationDefined && !implemented"
        face.put("calculationRequired", false);
        if (Boolean.TRUE.equals(face.get("needsConfiguration"))
                && "CALCULATION_REQUIRED".equals(String.valueOf(face.get("executionReadinessCause")))) {
            face.put("needsConfiguration", false);
            face.remove("executionReadinessCause");
            face.remove("message");
            // Restore derived-automatically wording when we forced NEEDS_CONFIG solely for missing calc
            String avail = String.valueOf(face.getOrDefault("availability", ""));
            if ("NEEDS_CONFIGURATION".equalsIgnoreCase(avail)
                    || "NEEDS_CONFIG".equalsIgnoreCase(avail)) {
                face.put("availability", "DERIVED");
                face.put("availabilityLabel", "Derived automatically");
            }
        }
        String st = d.getStatus() == null ? "" : d.getStatus().toUpperCase(Locale.ROOT);
        if (DerivedCalculationDefinitionService.STATUS_TESTED.equals(st)
                || DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY.equals(st)
                || DerivedCalculationDefinitionService.STATUS_DEFINED.equals(st)) {
            // DEFINED is enough for Policy Test once human-approved into the catalogue path
            face.put("policyTestReady", true);
        }
        if (DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY.equals(st)) {
            face.put("calculationProductionReady", true);
        }
    }
}
