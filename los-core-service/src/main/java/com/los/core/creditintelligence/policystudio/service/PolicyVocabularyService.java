package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CanonicalObjectType;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PolicyVocabularyService {

    private final List<CiPolicyVocabulary> store = new CopyOnWriteArrayList<>();

    public PolicyVocabularyService() {
        seed();
    }

    private void seed() {
        store.add(vocab(null, "GLOBAL", null, "ABB", "Average Bank Balance / Average Daily Balance",
                "banking.avg_daily_balance_3m", List.of("Average Bank Balance", "ADB", "Average Daily Balance")));
        store.add(vocab(null, "GLOBAL", null, "ADB", "Average Daily Balance last 3 months",
                "banking.avg_daily_balance_3m", List.of("Average Daily Balance", "ABB")));
        store.add(vocab(null, "GLOBAL", null, "Average Daily Balance", "ADB trailing 3 months",
                "banking.avg_daily_balance_3m", List.of("ADB", "ABB")));
        // EDI intentionally NOT seeded
        store.add(vocab(null, "GLOBAL", null, "Bureau Score", "Canonical bureau score",
                "bureau.score", List.of("score")));
        store.add(vocab(null, "GLOBAL", null, "DPD", "Days Past Due",
                "bureau.max_dpd_6m", List.of("Days Past Due")));
    }

    private CiPolicyVocabulary vocab(UUID tenantId, String scope, String product, String term,
                                     String meaning, String path, List<Object> synonyms) {
        return CiPolicyVocabulary.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .scopeLevel(scope)
                .productCode(product)
                .term(term)
                .canonicalMeaning(meaning)
                .canonicalPath(path)
                .objectType(CanonicalObjectType.METRIC.name())
                .synonyms(synonyms)
                .status("ACTIVE")
                .approvedBy("SYSTEM_SEED")
                .version(1)
                .approvedAt(Instant.now())
                .metadata(Map.of("seed", true))
                .build();
    }

    public List<CiPolicyVocabulary> globalSeed() {
        return store.stream().filter(v -> "GLOBAL".equals(v.getScopeLevel())).toList();
    }

    /**
     * Approve a term at GLOBAL / TENANT / PRODUCT scope. Versioned; prior approval noted.
     * Next interpret proposes prior mapping first but does not auto-finalize.
     */
    public CiPolicyVocabulary approveTerm(
            UUID tenantId,
            String scopeLevel,
            String productCode,
            String term,
            String canonicalMeaning,
            String canonicalPath,
            String objectType,
            String approvedBy,
            String previouslyApprovedNote) {
        String scope = scopeLevel == null ? "TENANT" : scopeLevel.toUpperCase(Locale.ROOT);
        CiPolicyVocabulary prior = findBest(tenantId, productCode, term);
        int nextVersion = prior == null ? 1 : (prior.getVersion() == null ? 1 : prior.getVersion() + 1);
        if (prior != null) {
            prior.setStatus("SUPERSEDED");
            prior.setEffectiveTo(Instant.now());
        }
        CiPolicyVocabulary v = CiPolicyVocabulary.builder()
                .id(UUID.randomUUID())
                .tenantId("GLOBAL".equals(scope) ? null : tenantId)
                .scopeLevel(scope)
                .productCode(productCode)
                .term(term)
                .canonicalMeaning(canonicalMeaning)
                .canonicalPath(canonicalPath)
                .objectType(objectType == null ? CanonicalObjectType.METRIC.name() : objectType)
                .synonyms(List.of())
                .status("ACTIVE")
                .approvedBy(approvedBy)
                .version(nextVersion)
                .previousVersionId(prior == null ? null : prior.getId())
                .approvedAt(Instant.now())
                .previouslyApprovedNote(previouslyApprovedNote != null ? previouslyApprovedNote
                        : (prior == null ? null : "Supersedes version " + prior.getVersion()))
                .metadata(Map.of(
                        "proposeFirst", true,
                        "autoFinalize", false))
                .build();
        store.add(v);
        return v;
    }

    public CiPolicyVocabulary resolve(String phrase) {
        return resolve(null, null, phrase);
    }

    public CiPolicyVocabulary resolve(UUID tenantId, String productCode, String phrase) {
        if (phrase == null) {
            return null;
        }
        String p = phrase.trim().toLowerCase(Locale.ROOT);
        if (p.equals("edi") || p.contains("proposed edi")) {
            // only if explicitly approved in vocabulary
            CiPolicyVocabulary approved = findBest(tenantId, productCode, "EDI");
            if (approved == null || Boolean.TRUE.equals(
                    approved.getMetadata() != null ? approved.getMetadata().get("seed") : null)) {
                return null;
            }
            return approved;
        }
        return findBest(tenantId, productCode, phrase);
    }

    /** Prefer PRODUCT → TENANT → GLOBAL; propose prior mapping first (caller must not auto-finalize). */
    public List<CiPolicyVocabulary> proposePriorMappings(UUID tenantId, String productCode, String phrase) {
        List<CiPolicyVocabulary> hits = new ArrayList<>();
        CiPolicyVocabulary best = findBest(tenantId, productCode, phrase);
        if (best != null) {
            hits.add(best);
        }
        return hits;
    }

    private CiPolicyVocabulary findBest(UUID tenantId, String productCode, String phrase) {
        if (phrase == null) {
            return null;
        }
        return store.stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()))
                .filter(v -> matchesTerm(v, phrase))
                .filter(v -> scopeApplies(v, tenantId, productCode))
                .sorted(Comparator
                        .comparingInt((CiPolicyVocabulary v) -> scopeRank(v.getScopeLevel())).reversed()
                        .thenComparing(v -> v.getVersion() == null ? 0 : v.getVersion(), Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesTerm(CiPolicyVocabulary v, String phrase) {
        if (v.getTerm().equalsIgnoreCase(phrase)) {
            return true;
        }
        String p = phrase.trim().toLowerCase(Locale.ROOT);
        for (Object s : v.getSynonyms()) {
            if (String.valueOf(s).equalsIgnoreCase(phrase)
                    || p.contains(String.valueOf(s).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean scopeApplies(CiPolicyVocabulary v, UUID tenantId, String productCode) {
        String scope = v.getScopeLevel();
        if ("GLOBAL".equals(scope)) {
            return true;
        }
        if ("TENANT".equals(scope)) {
            return tenantId != null && tenantId.equals(v.getTenantId());
        }
        if ("PRODUCT".equals(scope)) {
            return tenantId != null && tenantId.equals(v.getTenantId())
                    && productCode != null && productCode.equalsIgnoreCase(v.getProductCode());
        }
        return false;
    }

    private int scopeRank(String scope) {
        if ("PRODUCT".equals(scope)) return 3;
        if ("TENANT".equals(scope)) return 2;
        return 1;
    }
}
