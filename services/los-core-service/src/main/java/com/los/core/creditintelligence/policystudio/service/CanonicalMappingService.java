package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CanonicalObjectType;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMappingCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CanonicalMappingService {

    private final PolicyAuthoringRegistry registry;
    private final PolicyVocabularyService vocabularyService;

    public CanonicalMappingService(PolicyAuthoringRegistry registry, PolicyVocabularyService vocabularyService) {
        this.registry = registry;
        this.vocabularyService = vocabularyService;
    }

    public CanonicalMappingService() {
        this(new PolicyAuthoringRegistry(), new PolicyVocabularyService());
    }

    /**
     * AI/system ranking only — never auto-finalizes (selected remains false unless human selects).
     */
    public List<CiPolicyMappingCandidate> map(
            List<CiPolicyClause> clauses,
            List<CiPolicyInterpretation> interpretations) {
        List<CiPolicyMappingCandidate> out = new ArrayList<>();
        Map<UUID, CiPolicyInterpretation> byClause = new java.util.HashMap<>();
        for (CiPolicyInterpretation i : interpretations) {
            byClause.put(i.getClauseId(), i);
        }
        for (CiPolicyClause clause : clauses) {
            CiPolicyInterpretation interp = byClause.get(clause.getId());
            if (interp == null) {
                continue;
            }
            for (Object input : interp.getCandidateInputs()) {
                String phrase = String.valueOf(input);
                out.addAll(candidatesFor(interp.getId(), phrase));
            }
            // phrase-level from source
            String lower = clause.getSourceText().toLowerCase(Locale.ROOT);
            if (lower.contains("edi")) {
                out.addAll(candidatesFor(interp.getId(), "EDI"));
            }
            if (lower.contains("average daily balance") || lower.contains("adb")) {
                out.addAll(candidatesFor(interp.getId(), "Average Daily Balance"));
            }
            if (lower.contains("average monthly transaction")) {
                out.addAll(candidatesFor(interp.getId(), "Average monthly transactions"));
            }
        }
        // de-dupe by interpretation+path+phrase
        Map<String, CiPolicyMappingCandidate> unique = new java.util.LinkedHashMap<>();
        for (CiPolicyMappingCandidate m : out) {
            unique.putIfAbsent(m.getInterpretationId() + "|" + m.getCanonicalPath() + "|" + m.getSourcePhrase(), m);
        }
        return new ArrayList<>(unique.values());
    }

    public List<CiPolicyMappingCandidate> candidatesFor(UUID interpretationId, String phrase) {
        List<CiPolicyMappingCandidate> out = new ArrayList<>();
        int rank = 1;
        CiPolicyVocabulary vocab = vocabularyService.resolve(phrase);
        if (vocab != null && vocab.getCanonicalPath() != null) {
            out.add(cand(interpretationId, phrase, vocab.getCanonicalPath(),
                    vocab.getObjectType(), "SYSTEM_VOCABULARY", 0.92, rank++));
        }
        for (Map<String, Object> hit : registry.findMetricCandidates(phrase)) {
            String code = String.valueOf(hit.get("code"));
            if (vocab != null && code.equals(vocab.getCanonicalPath())) {
                continue;
            }
            boolean invented = !registry.hasCanonicalPath(code);
            if (invented) {
                continue;
            }
            String type = String.valueOf(hit.getOrDefault("type", "METRIC"));
            double conf = rank == 1 ? 0.80 : 0.61;
            out.add(cand(interpretationId, phrase, code, type,
                    rank == 1 ? "SYSTEM_EXACT" : "AI", conf, rank++));
        }
        if (out.isEmpty() && phrase.toLowerCase(Locale.ROOT).contains("edi")) {
            out.add(cand(interpretationId, phrase, "application.proposed_edi",
                    CanonicalObjectType.APPLICATION_FIELD.name(), "AI", 0.55, 1));
        }
        // never set selected=true from AI alone
        return out;
    }

    private CiPolicyMappingCandidate cand(UUID interpId, String phrase, String path, String objType,
                                          String basis, double conf, int rank) {
        return CiPolicyMappingCandidate.builder()
                .id(UUID.randomUUID())
                .interpretationId(interpId)
                .sourcePhrase(phrase)
                .candidateType(objType)
                .canonicalPath(path)
                .canonicalObjectType(objType)
                .confidence(BigDecimal.valueOf(conf))
                .matchBasis(basis)
                .rank(rank)
                .selected(false)
                .selectionSource(null)
                .metadata(Map.of("aiDoesNotFinalize", true))
                .build();
    }
}
