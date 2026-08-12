package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unified READ MODEL facade (POLICY-CONVERGENCE / GACAT-PERSISTENCE-1).
 * Application-facing abstraction for all consumers — backed by DB in runtime,
 * optionally by {@link GacatCatalogueSeed} for unit tests only.
 */
public class CanonicalParameterRegistry {

    private static final AtomicReference<CanonicalParameterRegistry> INSTALLED = new AtomicReference<>();

    private final List<CanonicalParameterDefinition> all;
    private final String authority;
    private final String inventoryVersion;

    /** Unit-test convenience — loads Java seed. Not used as production runtime authority. */
    public CanonicalParameterRegistry() {
        this(GacatCatalogueSeed.all(), GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
    }

    public CanonicalParameterRegistry(List<CanonicalParameterDefinition> definitions) {
        this(definitions, GacatCatalogueAuthority.AUTHORITY_DATABASE);
    }

    public CanonicalParameterRegistry(List<CanonicalParameterDefinition> definitions, String authority) {
        this.all = List.copyOf(definitions == null ? List.of() : definitions);
        this.authority = authority == null ? GacatCatalogueAuthority.AUTHORITY_UNLOADED : authority;
        this.inventoryVersion = GacatCatalogueAuthority.AUTHORITY_DATABASE.equals(this.authority)
                ? "GACAT-PERSISTENCE-1"
                : "GACAT-SOURCE-CATALOGUE-RECOVERY-1";
    }

    public static CanonicalParameterRegistry fromSeedForTestsOnly() {
        return new CanonicalParameterRegistry(
                GacatCatalogueSeed.all(), GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
    }

    public static void install(CanonicalParameterRegistry registry, String authority) {
        if (registry == null) {
            throw new IllegalArgumentException("registry");
        }
        INSTALLED.set(registry);
        GacatCatalogueAuthority.markAuthority(authority);
    }

    public static void clearInstalledForTests() {
        INSTALLED.set(null);
        GacatCatalogueAuthority.markAuthority(GacatCatalogueAuthority.AUTHORITY_UNLOADED);
        GacatCatalogueAuthority.configure(false, true);
    }

    /**
     * Shared registry for presenters/services. Prefers Spring-installed DB snapshot.
     * Falls back to Java seed only when {@link GacatCatalogueAuthority#seedFallbackAllowed()}.
     */
    public static CanonicalParameterRegistry shared() {
        CanonicalParameterRegistry installed = INSTALLED.get();
        if (installed != null) {
            return installed;
        }
        if (GacatCatalogueAuthority.requireDatabase()) {
            throw new IllegalStateException(
                    "GACAT catalogue not loaded from database (silent Java-seed fallback forbidden)");
        }
        if (!GacatCatalogueAuthority.seedFallbackAllowed()) {
            throw new IllegalStateException("GACAT catalogue unloaded and seed fallback disabled");
        }
        return fromSeedForTestsOnly();
    }

    public String authority() {
        return authority;
    }

    public String inventoryVersion() {
        return inventoryVersion;
    }

    public List<CanonicalParameterDefinition> all() {
        return all;
    }

    public Optional<CanonicalParameterDefinition> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String key = id.trim();
        return all.stream().filter(p -> key.equalsIgnoreCase(p.id())
                || key.equalsIgnoreCase(p.liveRuleParameter())
                || key.equalsIgnoreCase(p.liveScorecardParameter())).findFirst();
    }

    public Optional<CanonicalParameterDefinition> resolve(String phrase) {
        if (phrase == null || phrase.isBlank()) return Optional.empty();
        String q = phrase.trim().toLowerCase(Locale.ROOT);

        for (CanonicalParameterDefinition p : all) {
            if (p.id() != null && q.equals(p.id().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
            if (p.businessName() != null && q.equals(p.businessName().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
            if (p.liveRuleParameter() != null && q.equals(p.liveRuleParameter().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
            if (p.liveScorecardParameter() != null
                    && q.equals(p.liveScorecardParameter().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
        }
        for (CanonicalParameterDefinition p : all) {
            if (p.aliases() == null) continue;
            for (String a : p.aliases()) {
                if (a != null && q.equals(a.toLowerCase(Locale.ROOT))) {
                    return Optional.of(p);
                }
            }
        }
        for (CanonicalParameterDefinition p : all) {
            if (p.aliases() == null) continue;
            for (String a : p.aliases()) {
                if (a != null && !a.isBlank() && q.contains(a.toLowerCase(Locale.ROOT))) {
                    return Optional.of(p);
                }
            }
            if (p.businessName() != null && q.contains(p.businessName().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public List<CanonicalParameterDefinition> searchCompatibleCleanDefinitions() {
        List<CanonicalParameterDefinition> out = new ArrayList<>();
        for (CanonicalParameterDefinition p : all) {
            String id = p.id() == null ? "" : p.id().toLowerCase(Locale.ROOT);
            String name = p.businessName() == null ? "" : p.businessName().toLowerCase(Locale.ROOT);
            if (id.contains("clean") || name.contains("clean history") || name.contains("repayment history")) {
                out.add(p);
            }
        }
        return out;
    }

    public Map<String, Object> catalogueView() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", all.size());
        out.put("parameters", all.stream().map(CanonicalParameterDefinition::toBusinessView).toList());
        out.put("sources", sources());
        out.put("readModelOnly", true);
        out.put("allowCanonicalAuthority", false);
        out.put("inventoryVersion", inventoryVersion);
        out.put("catalogueAuthority", authority);
        out.put("javaSeedIsRuntimeAuthority",
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY.equals(authority));
        return out;
    }

    public List<String> sources() {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        for (String s : List.of(
                "Application",
                "Bureau Retail",
                "Bureau Commercial",
                "Bank Statement",
                "Account Aggregator",
                "GST",
                "Financial Statements",
                "KYC",
                "Program / Product",
                "Customer / Borrower",
                "Manual Input",
                "Computed / Derived")) {
            ordered.put(s, Boolean.FALSE);
        }
        for (CanonicalParameterDefinition p : all) {
            if (p.evaluatedFrom() != null && !p.evaluatedFrom().isBlank()) {
                ordered.putIfAbsent(p.evaluatedFrom(), Boolean.TRUE);
            }
        }
        return new ArrayList<>(ordered.keySet());
    }

    public Map<String, Object> browseBySource(String source) {
        String src = source == null ? "" : source.trim();
        List<Map<String, Object>> raw = new ArrayList<>();
        List<Map<String, Object>> derived = new ArrayList<>();
        List<Map<String, Object>> manual = new ArrayList<>();
        for (CanonicalParameterDefinition p : all) {
            if (!sourceMatches(p.evaluatedFrom(), src)) continue;
            Map<String, Object> view = p.toBusinessView();
            if (CanonicalParameterDefinition.RAW.equals(p.type())) raw.add(view);
            else if (CanonicalParameterDefinition.MANUAL.equals(p.type())) manual.add(view);
            else derived.add(view);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", src);
        out.put("raw", raw);
        out.put("derived", derived);
        out.put("manual", manual);
        out.put("count", raw.size() + derived.size() + manual.size());
        out.put("rawCount", raw.size());
        out.put("derivedCount", derived.size());
        out.put("manualCount", manual.size());
        long live = raw.stream().filter(r -> Boolean.TRUE.equals(r.get("productionReady"))).count()
                + derived.stream().filter(r -> Boolean.TRUE.equals(r.get("productionReady"))).count()
                + manual.stream().filter(r -> Boolean.TRUE.equals(r.get("productionReady"))).count();
        out.put("liveCount", live);
        out.put("invented", false);
        out.put("allowCanonicalAuthority", false);
        out.put("catalogueAuthority", authority);
        return out;
    }

    public Map<String, Object> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> hits = new ArrayList<>();
        if (!q.isBlank()) {
            for (CanonicalParameterDefinition p : all) {
                int score = matchScore(p, q);
                if (score <= 0) continue;
                Map<String, Object> row = new LinkedHashMap<>(p.toBusinessView());
                row.put("matchScore", score);
                hits.add(row);
            }
            hits.sort((a, b) -> Integer.compare(
                    ((Number) b.getOrDefault("matchScore", 0)).intValue(),
                    ((Number) a.getOrDefault("matchScore", 0)).intValue()));
        }
        LinkedHashMap<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        for (Map<String, Object> h : hits) {
            dedup.putIfAbsent(String.valueOf(h.get("id")), h);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("count", dedup.size());
        out.put("results", new ArrayList<>(dedup.values()));
        out.put("createsParameter", false);
        out.put("allowCanonicalAuthority", false);
        out.put("catalogueAuthority", authority);
        return out;
    }

    private static boolean sourceMatches(String evaluatedFrom, String selected) {
        if (selected == null || selected.isBlank()) return true;
        if (evaluatedFrom == null) return false;
        String sel = selected.trim().toLowerCase(Locale.ROOT);
        String from = evaluatedFrom.toLowerCase(Locale.ROOT);
        if (from.equals(sel) || from.contains(sel) || sel.contains(from)) return true;
        if ("bureau".equals(sel) && from.contains("bureau")) return true;
        return false;
    }

    private static int matchScore(CanonicalParameterDefinition p, String q) {
        int score = 0;
        if (p.id() != null && p.id().toLowerCase(Locale.ROOT).contains(q)) score = Math.max(score, 80);
        if (p.businessName() != null) {
            String n = p.businessName().toLowerCase(Locale.ROOT);
            if (n.equals(q)) score = Math.max(score, 100);
            else if (n.contains(q) || q.contains(n)) score = Math.max(score, 90);
        }
        if (p.evaluatedFrom() != null && p.evaluatedFrom().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 40);
        }
        if (p.liveRuleParameter() != null && p.liveRuleParameter().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 85);
        }
        if (p.liveScorecardParameter() != null
                && p.liveScorecardParameter().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 85);
        }
        if (p.aliases() != null) {
            for (String a : p.aliases()) {
                if (a == null) continue;
                // GATE2: token-safe alias match — "edi" must not hit via "credit"
                if (BusinessConceptMatching.aliasMatches(q, a)) {
                    String al = BusinessConceptMatching.normalize(a);
                    score = Math.max(score, al.length() <= 3 ? 96 : 98);
                }
            }
        }
        // Suppress Proposed EDI when query is a write-off / credit-card concept
        if ("application.proposed_edi".equals(p.id())
                && (BusinessConceptMatching.isWriteOffPhrase(q)
                || (q.contains("credit") && !BusinessConceptMatching.isProposedEdiPhrase(q)))) {
            return 0;
        }
        if (p.capability() != null && p.capability().providerFieldPath() != null
                && p.capability().providerFieldPath().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 70);
        }
        if (p.calculationSummary() != null && p.calculationSummary().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 50);
        }
        return score;
    }
}
