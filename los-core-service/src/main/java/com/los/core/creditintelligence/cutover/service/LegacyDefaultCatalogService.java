package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.CiLegacyDefaultDefinition;
import com.los.core.creditintelligence.cutover.domain.DefaultClassification;
import com.los.core.creditintelligence.cutover.domain.DefaultDefinitionStatus;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import com.los.core.creditintelligence.validation.service.LegacyDefaultInventory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loads / seeds legacy default catalog from {@link LegacyDefaultInventory} + CreditControl known keys.
 */
@Service
public class LegacyDefaultCatalogService {

    public static final String COMPONENT = "CreditControlService";
    public static final String FILE =
            "com/los/core/service/credit/CreditControlService.java";

    private final CutoverStore store;
    private final LegacyDefaultInventory inventory;

    public LegacyDefaultCatalogService(CutoverStore store) {
        this(store, new LegacyDefaultInventory());
    }

    @Autowired
    public LegacyDefaultCatalogService(CutoverStore store, LegacyDefaultInventory inventory) {
        this.store = store;
        this.inventory = inventory != null ? inventory : new LegacyDefaultInventory();
    }

    public List<CiLegacyDefaultDefinition> seedFromInventory() {
        if (!store.listDefaults().isEmpty()) {
            return store.listDefaults();
        }
        List<CiLegacyDefaultDefinition> out = new ArrayList<>();
        for (LegacyDefaultInventory.LegacyDefaultEntry e : inventory.inventory()) {
            out.add(store.saveDefault(mapEntry(e)));
        }
        // Explicit business policy default (not a silent gap)
        out.add(store.saveDefault(CiLegacyDefaultDefinition.builder()
                .legacyKey("PRICING_FLOOR_BPS")
                .component("DecisionStrategy")
                .filePath("com/los/core/creditintelligence/decision/service/PricingEngine.java")
                .methodName("price")
                .defaultValue("1200")
                .valueType("BPS")
                .triggerCondition("explicit strategy floor when configured")
                .products(List.of("ALL"))
                .rulesImpacted(List.of("PRICING"))
                .severity("LOW")
                .classification(DefaultClassification.BUSINESS_POLICY_DEFAULT.name())
                .canonicalReplacement("strategy:pricing.floorBps")
                .missingDataBehavior("NOT_APPLICABLE")
                .status(DefaultDefinitionStatus.MAPPED.name())
                .createdAt(Instant.now())
                .build()));
        return out;
    }

    public List<CiLegacyDefaultDefinition> listAll() {
        List<CiLegacyDefaultDefinition> existing = store.listDefaults();
        if (existing.isEmpty()) {
            return seedFromInventory();
        }
        return existing;
    }

    public long countUnsafeSilent() {
        return listAll().stream()
                .filter(d -> DefaultClassification.UNSAFE_SILENT_DEFAULT.name().equals(d.getClassification()))
                .filter(d -> !DefaultDefinitionStatus.REMOVED.name().equals(d.getStatus())
                        && !DefaultDefinitionStatus.QUARANTINED.name().equals(d.getStatus()))
                .count();
    }

    public long countQuarantined() {
        return listAll().stream()
                .filter(d -> DefaultDefinitionStatus.QUARANTINED.name().equals(d.getStatus()))
                .count();
    }

    private CiLegacyDefaultDefinition mapEntry(LegacyDefaultInventory.LegacyDefaultEntry e) {
        DefaultClassification classification = classify(e);
        String status = classification == DefaultClassification.UNSAFE_SILENT_DEFAULT
                || classification == DefaultClassification.DATA_GAP_FALLBACK
                ? DefaultDefinitionStatus.MAPPED.name()
                : DefaultDefinitionStatus.DISCOVERED.name();
        return CiLegacyDefaultDefinition.builder()
                .id(UUID.randomUUID())
                .legacyKey(e.legacyKey())
                .component(COMPONENT)
                .filePath(FILE)
                .methodName("applyMissingScorecardDefaults")
                .defaultValue(e.defaultValue())
                .valueType(inferType(e.defaultValue()))
                .triggerCondition("gap/demo default when source absent — origin=" + e.origin())
                .products(List.of("ALL"))
                .rulesImpacted(e.rulesImpacted() != null ? new ArrayList<>(e.rulesImpacted()) : List.of())
                .severity(e.critical() ? "CRITICAL" : "MEDIUM")
                .classification(classification.name())
                .canonicalReplacement(e.canonicalPath())
                .missingDataBehavior(missingBehavior(classification))
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    static DefaultClassification classify(LegacyDefaultInventory.LegacyDefaultEntry e) {
        String origin = e.origin() == null ? "" : e.origin();
        if (origin.contains("DEMO")) {
            return DefaultClassification.DEMO_ONLY;
        }
        if ("FLAG".equals(origin) && "PROVIDER_GAP_DEFAULT_ACTIVE".equals(e.legacyKey())) {
            return DefaultClassification.UNSAFE_SILENT_DEFAULT;
        }
        if (origin.contains("GAP")) {
            // Silent numeric underwriting fill — unsafe
            return DefaultClassification.UNSAFE_SILENT_DEFAULT;
        }
        return DefaultClassification.DATA_GAP_FALLBACK;
    }

    private static String missingBehavior(DefaultClassification c) {
        return switch (c) {
            case DEMO_ONLY -> "REFER";
            case BUSINESS_POLICY_DEFAULT -> "NOT_APPLICABLE";
            default -> "DATA_INSUFFICIENT";
        };
    }

    private static String inferType(String v) {
        if (v == null) return "STRING";
        if (v.contains(".")) return "DECIMAL";
        try {
            Integer.parseInt(v);
            return "INTEGER";
        } catch (NumberFormatException e) {
            return "STRING";
        }
    }
}
