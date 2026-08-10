package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * POLICY-UX-2A — universal credit capability catalogue read model.
 * Does not alter production underwriting configuration or authority.
 */
@Service
public class CreditCapabilityCatalogueService {

    private final List<BusinessCapability> capabilities;
    private final CreditIntelligenceProperties properties;

    public CreditCapabilityCatalogueService(CreditIntelligenceProperties properties) {
        this.properties = properties;
        this.capabilities = CreditCapabilityDefinitions.all();
        assertUniqueIds(this.capabilities);
    }

    /** Test / standalone construction without Spring. */
    public CreditCapabilityCatalogueService() {
        this(new CreditIntelligenceProperties());
    }

    public List<BusinessCapability> listCapabilities() {
        return capabilities;
    }

    public Optional<BusinessCapability> findById(String businessCapabilityId) {
        if (businessCapabilityId == null) {
            return Optional.empty();
        }
        return capabilities.stream()
                .filter(c -> businessCapabilityId.equals(c.businessCapabilityId()))
                .findFirst();
    }

    /**
     * Credit Manager facing catalogue payload.
     *
     * @param advanced when true, include engine/class binding details and normalization notes
     */
    public Map<String, Object> catalogueView(boolean advanced) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Credit capability catalogue");
        out.put("purpose", "Normalized underwriting capabilities for Policy Studio — read model only");
        out.put("allowCanonicalAuthority", allowCanonicalAuthority());
        out.put("productionAuthority", "DISABLED");
        out.put("capabilityCount", capabilities.size());

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (CapabilityDomain domain : CapabilityDomain.values()) {
            List<Map<String, Object>> items = capabilities.stream()
                    .filter(c -> c.domain() == domain)
                    .map(c -> c.toBusinessView(advanced))
                    .toList();
            if (!items.isEmpty()) {
                groups.put(domain.displayName(), items);
            }
        }
        out.put("groups", groups);
        out.put("capabilities", capabilities.stream().map(c -> c.toBusinessView(advanced)).toList());

        if (advanced) {
            out.put("normalizationNotes", CreditCapabilityDefinitions.normalizationNotes());
            out.put("productionFixtureMappings",
                    ProductionUnderwritingCapabilityAdapter.mapHardRules(
                            ProductionUnderwritingCapabilityAdapter.scfFixtureHardRules()));
        }
        return out;
    }

    public Map<String, Object> mapProductionHardRule(Map<String, Object> hardRule) {
        Map<String, Object> mapped = ProductionUnderwritingCapabilityAdapter.mapHardRule(hardRule);
        Object id = mapped.get("businessCapabilityId");
        if (id != null) {
            findById(String.valueOf(id)).ifPresent(cap ->
                    mapped.put("capability", cap.toBusinessView(false)));
        }
        mapped.put("allowCanonicalAuthority", allowCanonicalAuthority());
        return mapped;
    }

    public Map<String, Object> mapProductionFixtureSample() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "SCF_V81_FIXTURE_HARD_RULES");
        out.put("note", "Read-only mapping of seeded SCF hardRules — production config not modified");
        out.put("allowCanonicalAuthority", allowCanonicalAuthority());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> hr : ProductionUnderwritingCapabilityAdapter.scfFixtureHardRules()) {
            rows.add(mapProductionHardRule(hr));
        }
        out.put("mappings", rows);
        long matched = rows.stream().filter(r -> Boolean.TRUE.equals(r.get("matched"))).count();
        out.put("matchedCount", matched);
        out.put("totalCount", rows.size());
        return out;
    }

    private boolean allowCanonicalAuthority() {
        if (properties == null || properties.getCutover() == null) {
            return false;
        }
        return properties.getCutover().isAllowCanonicalAuthority();
    }

    private static void assertUniqueIds(List<BusinessCapability> caps) {
        Set<String> seen = new LinkedHashSet<>();
        for (BusinessCapability c : caps) {
            if (!seen.add(c.businessCapabilityId())) {
                throw new IllegalStateException("Duplicate businessCapabilityId: " + c.businessCapabilityId());
            }
        }
    }
}
