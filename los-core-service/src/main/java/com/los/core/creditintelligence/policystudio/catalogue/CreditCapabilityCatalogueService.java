package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthorableParameterProjection;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * POLICY-UX-2A/2C — universal credit capability catalogue read model.
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
        Optional<BusinessCapability> legacy = capabilities.stream()
                .filter(c -> businessCapabilityId.equals(c.businessCapabilityId()))
                .findFirst();
        if (legacy.isPresent()) {
            return legacy;
        }
        return GacatPolicyAuthorableCapabilityFactory.findAuthorable(registry(), businessCapabilityId);
    }

    /**
     * Credit Manager facing catalogue payload.
     * Add Rule universe = GACAT {@code policySelectableDefault} (same as Change Parameter).
     * Legacy BUREAU.MIN_SCORE templates remain on {@link #listCapabilities()} for ingestion
     * matching only — never as Add Rule selectable capabilities, including advanced.
     *
     * @param advanced when true, attach ingestion-template documentation; does not enlarge the authorable set
     */
    public Map<String, Object> catalogueView(boolean advanced) {
        List<BusinessCapability> authorable = GacatPolicyAuthorableCapabilityFactory.all(registry());
        List<BusinessCapability> visible = new ArrayList<>(authorable);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Credit capability catalogue");
        out.put("purpose", "GACAT authorable parameters for Policy Studio — same universe as Change Parameter");
        out.put("allowCanonicalAuthority", allowCanonicalAuthority());
        out.put("productionAuthority", "DISABLED");
        out.put("capabilityCount", authorable.size());
        out.put("totalCapabilityCount", authorable.size());
        out.put("legacyTemplateCount", capabilities.size());
        out.put("authorableProjectionAuthority", PolicyAuthorableParameterProjection.AUTHORITY);
        out.put("policyAuthorableOnly", true);
        out.put("legacyParameterCatalogueConsumerCount", 0);

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (CapabilityDomain domain : CapabilityDomain.values()) {
            List<Map<String, Object>> items = visible.stream()
                    .filter(c -> c.domain() == domain)
                    .map(c -> enrichView(c, advanced))
                    .toList();
            if (!items.isEmpty()) {
                groups.put(domain.displayName(), items);
            }
        }
        out.put("groups", groups);
        out.put("capabilities", visible.stream().map(c -> enrichView(c, advanced)).toList());

        List<Map<String, Object>> common = new ArrayList<>();
        for (BusinessCapability c : authorable) {
            if (c.factOrMeasure() != null && registry().findById(c.factOrMeasure())
                    .map(d -> d.liveRuleParameter() != null && !d.liveRuleParameter().isBlank())
                    .orElse(false)) {
                common.add(enrichView(c, advanced));
            }
            if (common.size() >= 8) {
                break;
            }
        }
        if (common.isEmpty()) {
            authorable.stream().limit(8).forEach(c -> common.add(enrichView(c, advanced)));
        }
        out.put("commonCapabilities", common);
        out.put("searchAliases", CreditCapabilityDefinitions.searchAliases());
        out.put("bureauDuplicateResolution", Map.of(
                "preferredCapabilityId", "bureau.score",
                "advancedAliasCapabilityId", "BUREAU.MIN_SCORE",
                "note", "Add Rule uses GACAT canonical IDs. Legacy BUREAU.MIN_SCORE remains an advanced template "
                        + "and an ingestion-matcher id — not a second authorable universe."));

        if (advanced) {
            out.put("normalizationNotes", CreditCapabilityDefinitions.normalizationNotes());
            out.put("productionFixtureMappings",
                    ProductionUnderwritingCapabilityAdapter.mapHardRules(
                            ProductionUnderwritingCapabilityAdapter.scfFixtureHardRules()));
            out.put("legacyTemplates", capabilities.stream().map(c -> enrichView(c, true)).toList());
        }
        return out;
    }

    public Map<String, Object> search(String query, boolean advanced) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> view = catalogueView(advanced);
        if (q.isBlank()) {
            return view;
        }
        Set<String> aliasHits = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> e : CreditCapabilityDefinitions.searchAliases().entrySet()) {
            if (e.getKey().contains(q) || q.contains(e.getKey())) {
                aliasHits.addAll(e.getValue());
            }
        }
        expandLegacyAliasHitsToCanonicalIds(aliasHits);
        List<BusinessCapability> haystack = new ArrayList<>(GacatPolicyAuthorableCapabilityFactory.all(registry()));
        List<Map<String, Object>> matched = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (BusinessCapability c : haystack) {
            if (!seen.add(c.businessCapabilityId())) {
                continue;
            }
            String hay = (c.businessCapabilityId() + " " + c.businessName() + " " + c.description() + " "
                    + c.factOrMeasure() + " " + c.dataSource()).toLowerCase(Locale.ROOT);
            if (hay.contains(q) || aliasHits.contains(c.businessCapabilityId())) {
                matched.add(enrichView(c, advanced));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(view);
        out.put("query", query);
        out.put("matchCount", matched.size());
        out.put("matches", matched);
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> item : matched) {
            String label = String.valueOf(item.getOrDefault("domainLabel", "Other"));
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(item);
        }
        out.put("groups", groups);
        out.put("capabilityCount", matched.size());
        return out;
    }

    private void expandLegacyAliasHitsToCanonicalIds(Set<String> aliasHits) {
        for (String id : List.copyOf(aliasHits)) {
            capabilities.stream()
                    .filter(c -> id.equals(c.businessCapabilityId()))
                    .findFirst()
                    .ifPresent(c -> {
                        String fact = c.factOrMeasure();
                        if (fact == null) {
                            return;
                        }
                        for (String token : fact.split("[^A-Za-z0-9._]+")) {
                            if (token.contains(".")) {
                                aliasHits.add(token);
                            }
                        }
                    });
        }
    }

    private CanonicalParameterRegistry registry() {
        return CanonicalParameterRegistry.shared();
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

    /** SCF walkthrough representability check for UX-2C. */
    public Map<String, Object> scfRepresentability() {
        List<String> required = List.of(
                "BUREAU.MIN_SCORE",
                "BUREAU.LIVE_UNSECURED_MAX",
                "BUREAU.ENQUIRIES_MAX",
                "BANK.CHEQUE_BOUNCE_MAX",
                "BANK.TURNOVER_PCT_GST_MIN",
                "BANK.CC_UTIL_MAX",
                "GST.TURNOVER_MIN",
                "ELIG.BUSINESS_VINTAGE_MIN",
                "FIN.DSCR_MIN",
                "FIN.INTEREST_COVERAGE_MIN",
                "FIN.DEBT_EQUITY_MAX",
                "FIN.TOL_TNW_MAX",
                "LIMIT.ABS_CAP");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : required) {
            Optional<BusinessCapability> cap = findById(id);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessCapabilityId", id);
            if (cap.isEmpty()) {
                row.put("representable", false);
                missing.add(id);
            } else {
                row.put("representable", true);
                row.put("businessName", cap.get().businessName());
                row.put("parameters", cap.get().parameterDefinitions().stream()
                        .map(ParameterDefinition::name).toList());
            }
            rows.add(row);
        }
        // Cheque bounce needs both 3M and 12M via same capability + windowMonths param
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "SCF catalogue representability");
        out.put("representableCount", required.size() - missing.size());
        out.put("requiredCount", required.size());
        out.put("missing", missing);
        out.put("note", "BANK.CHEQUE_BOUNCE_MAX covers both 3M=0 and 12M<=6 via windowMonths + maximumCount");
        out.put("items", rows);
        out.put("allowCanonicalAuthority", allowCanonicalAuthority());
        return out;
    }

    private Map<String, Object> enrichView(BusinessCapability c, boolean advanced) {
        Map<String, Object> view = new LinkedHashMap<>(c.toBusinessView(advanced));
        boolean gacat = c.businessCapabilityId() != null && c.businessCapabilityId().contains(".")
                && c.businessCapabilityId().equals(c.factOrMeasure());
        view.put("canonicalParameterId", gacat ? c.businessCapabilityId() : null);
        view.put("gacatAuthorable", gacat);
        view.put("legacyCapabilityTemplate", !gacat);
        view.put("primaryCatalogue", gacat
                || CreditCapabilityDefinitions.primaryCatalogueVisible(c.businessCapabilityId()));
        if ("ELIG.MIN_BUREAU_SCORE".equals(c.businessCapabilityId())) {
            view.put("aliasOf", "BUREAU.MIN_SCORE");
            view.put("catalogueVisibility", "ADVANCED");
        } else {
            view.put("catalogueVisibility", gacat ? "PRIMARY" : (advanced ? "ADVANCED" : "PRIMARY"));
        }
        return view;
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
