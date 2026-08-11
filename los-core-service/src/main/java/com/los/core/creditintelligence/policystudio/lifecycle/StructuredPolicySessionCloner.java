package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAuthoringSession;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMappingCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyParameter;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.ParameterResolutionSupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * POLICY-VERSION-INTEGRITY-1 — structured clone of a Policy Studio session.
 * Copies persisted policy content (rules, scope metadata, resolutions, measures).
 * Does NOT re-run clause extraction / NLP / classification.
 * Governance (approvals, test sign-off, simulation) is reset on the clone.
 */
public final class StructuredPolicySessionCloner {

    public enum Mode {
        /** Same policy family, next version label (caller sets lifecycle). */
        NEW_VERSION,
        /** Separate draft / family (caller sets copy metadata). */
        COPY
    }

    private StructuredPolicySessionCloner() {}

    public static PolicyStudioSession cloneSession(
            PolicyStudioSession source,
            CiPolicyDocument targetDocument,
            Mode mode) {
        if (source == null || targetDocument == null || targetDocument.getId() == null) {
            throw new IllegalArgumentException("source session and target document required");
        }
        UUID targetDocId = targetDocument.getId();
        Map<UUID, UUID> clauseIds = new LinkedHashMap<>();
        Map<UUID, UUID> interpretationIds = new LinkedHashMap<>();
        Map<UUID, UUID> ruleIds = new LinkedHashMap<>();

        PolicyStudioSession target = new PolicyStudioSession();
        target.setDocument(targetDocument);
        targetDocument.setStatus(DocumentStatus.DRAFT_READY.name());

        // Clauses first (parent remap after all ids known)
        if (source.getClauses() != null) {
            for (CiPolicyClause c : source.getClauses()) {
                if (c == null || c.getId() == null) continue;
                UUID nid = UUID.randomUUID();
                clauseIds.put(c.getId(), nid);
            }
            for (CiPolicyClause c : source.getClauses()) {
                if (c == null || c.getId() == null) continue;
                UUID nid = clauseIds.get(c.getId());
                UUID parent = c.getParentClauseId() == null ? null
                        : clauseIds.getOrDefault(c.getParentClauseId(), c.getParentClauseId());
                Map<String, Object> meta = sanitizePolicyMeta(copyMap(c.getMetadata()), mode);
                meta.put("clonedFromClauseId", c.getId().toString());
                target.getClauses().add(CiPolicyClause.builder()
                        .id(nid)
                        .policyDocumentId(targetDocId)
                        .clauseNumber(c.getClauseNumber())
                        .parentClauseId(parent)
                        .section(c.getSection())
                        .page(c.getPage())
                        .sourceText(c.getSourceText())
                        .normalizedText(c.getNormalizedText())
                        .clauseType(c.getClauseType())
                        .productScope(c.getProductScope())
                        .effectiveScope(copyMap(c.getEffectiveScope()))
                        .sourceLocation(c.getSourceLocation())
                        .extractionConfidence(c.getExtractionConfidence())
                        .status(c.getStatus())
                        .sortOrder(c.getSortOrder())
                        .metadata(meta)
                        .build());
            }
        }

        if (source.getInterpretations() != null) {
            for (CiPolicyInterpretation i : source.getInterpretations()) {
                if (i == null || i.getId() == null) continue;
                UUID nid = UUID.randomUUID();
                interpretationIds.put(i.getId(), nid);
                UUID clauseId = i.getClauseId() == null ? null
                        : clauseIds.getOrDefault(i.getClauseId(), i.getClauseId());
                target.getInterpretations().add(CiPolicyInterpretation.builder()
                        .id(nid)
                        .clauseId(clauseId)
                        .interpretationVersion(i.getInterpretationVersion())
                        .interpretedClauseType(i.getInterpretedClauseType())
                        .naturalLanguageMeaning(i.getNaturalLanguageMeaning())
                        .candidateExpression(copyMap(i.getCandidateExpression()))
                        .candidateInputs(copyList(i.getCandidateInputs()))
                        .candidateOutputs(copyList(i.getCandidateOutputs()))
                        .candidateProductScope(copyList(i.getCandidateProductScope()))
                        .candidatePeriod(i.getCandidatePeriod())
                        .candidateThresholds(copyMap(i.getCandidateThresholds()))
                        .confidence(i.getConfidence())
                        .confidenceBreakdown(copyMap(i.getConfidenceBreakdown()))
                        .limitations(i.getLimitations())
                        .aiModel(i.getAiModel())
                        .aiModelVersion(i.getAiModelVersion())
                        .promptVersion(i.getPromptVersion())
                        .providerCode(i.getProviderCode())
                        .build());
            }
        }

        if (source.getMappings() != null) {
            for (CiPolicyMappingCandidate m : source.getMappings()) {
                if (m == null) continue;
                UUID interpId = m.getInterpretationId() == null ? null
                        : interpretationIds.getOrDefault(m.getInterpretationId(), m.getInterpretationId());
                target.getMappings().add(CiPolicyMappingCandidate.builder()
                        .id(UUID.randomUUID())
                        .interpretationId(interpId)
                        .sourcePhrase(m.getSourcePhrase())
                        .candidateType(m.getCandidateType())
                        .canonicalPath(m.getCanonicalPath())
                        .canonicalObjectType(m.getCanonicalObjectType())
                        .confidence(m.getConfidence())
                        .matchBasis(m.getMatchBasis())
                        .rank(m.getRank())
                        .selected(m.getSelected())
                        .selectionSource(m.getSelectionSource())
                        .selectedBy(m.getSelectedBy())
                        .selectedAt(m.getSelectedAt())
                        .reviewNotes(m.getReviewNotes())
                        .metadata(sanitizePolicyMeta(copyMap(m.getMetadata()), mode))
                        .build());
            }
        }

        if (source.getAmbiguities() != null) {
            for (CiPolicyAmbiguity a : source.getAmbiguities()) {
                if (a == null) continue;
                UUID clauseId = a.getClauseId() == null ? null
                        : clauseIds.getOrDefault(a.getClauseId(), a.getClauseId());
                target.getAmbiguities().add(CiPolicyAmbiguity.builder()
                        .id(UUID.randomUUID())
                        .clauseId(clauseId)
                        .ambiguityType(a.getAmbiguityType())
                        .phrase(a.getPhrase())
                        .description(a.getDescription())
                        .candidateOptions(copyList(a.getCandidateOptions()))
                        .recommendedOption(a.getRecommendedOption())
                        .confidence(a.getConfidence())
                        .severity(a.getSeverity())
                        .resolutionStatus(a.getResolutionStatus())
                        .resolvedOption(a.getResolvedOption())
                        .resolvedBy(a.getResolvedBy())
                        .resolvedAt(a.getResolvedAt())
                        .resolutionNotes(a.getResolutionNotes())
                        .previousResolution(a.getPreviousResolution() == null
                                ? null : new ArrayList<>(a.getPreviousResolution()))
                        .metadata(sanitizePolicyMeta(copyMap(a.getMetadata()), mode))
                        .build());
            }
        }

        if (source.getMetricCandidates() != null) {
            for (CiPolicyMetricCandidate m : source.getMetricCandidates()) {
                if (m == null) continue;
                UUID clauseId = m.getClauseId() == null ? null
                        : clauseIds.getOrDefault(m.getClauseId(), m.getClauseId());
                Map<String, Object> meta = sanitizePolicyMeta(copyMap(m.getMetadata()), mode);
                meta.put("clonedFromMetricId", m.getId() == null ? null : m.getId().toString());
                target.getMetricCandidates().add(CiPolicyMetricCandidate.builder()
                        .id(UUID.randomUUID())
                        .clauseId(clauseId)
                        .metricName(m.getMetricName())
                        .baseMetric(m.getBaseMetric())
                        .expression(copyMap(m.getExpression()))
                        .exclusions(copyList(m.getExclusions()))
                        .inclusions(copyList(m.getInclusions()))
                        .period(m.getPeriod())
                        .aggregation(m.getAggregation())
                        .dependencies(copyList(m.getDependencies()))
                        .missingDataPolicy(m.getMissingDataPolicy())
                        .candidateCanonicalCode(m.getCandidateCanonicalCode())
                        .confidence(m.getConfidence())
                        .reviewStatus(m.getReviewStatus())
                        .systemMetricId(m.getSystemMetricId())
                        .metadata(meta)
                        .build());
            }
        }

        if (source.getRuleCandidates() != null) {
            for (CiPolicyRuleCandidate r : source.getRuleCandidates()) {
                if (r == null) continue;
                if (isDeleted(r)) continue;
                UUID newRuleId = UUID.randomUUID();
                if (r.getId() != null) {
                    ruleIds.put(r.getId(), newRuleId);
                }
                UUID clauseId = r.getClauseId() == null ? null
                        : clauseIds.getOrDefault(r.getClauseId(), r.getClauseId());
                Map<String, Object> meta = sanitizeRuleMeta(copyMap(r.getMetadata()), mode);
                meta.put("clonedFromRuleId", r.getId() == null ? null : r.getId().toString());
                Map<String, Object> lineage = copyMap(r.getLineage());
                lineage.put("clonedFromDocumentId", source.getDocument() == null ? null
                        : String.valueOf(source.getDocument().getId()));
                lineage.put("clonedFromRuleId", r.getId() == null ? null : r.getId().toString());
                lineage.put("cloneMode", mode.name());
                lineage.put("structuredClone", true);
                target.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                        .id(newRuleId)
                        .clauseId(clauseId)
                        .systemRuleId(r.getSystemRuleId())
                        .ruleVersion("DRAFT")
                        .ruleType(r.getRuleType())
                        .scope(copyMap(r.getScope()))
                        .expression(copyMap(r.getExpression()))
                        .onTrue(r.getOnTrue())
                        .onFalse(r.getOnFalse())
                        .onMissing(r.getOnMissing())
                        .confidence(r.getConfidence())
                        .reviewStatus(r.getReviewStatus())
                        .lineage(lineage)
                        .metadata(meta)
                        .unitLeft(r.getUnitLeft())
                        .unitRight(r.getUnitRight())
                        .periodSemantics(r.getPeriodSemantics())
                        .validationErrors(r.getValidationErrors() == null
                                ? List.of() : new ArrayList<>(r.getValidationErrors()))
                        .build());
            }
        }

        // Test cases: keep structure for authoring, reset approval evidence
        if (source.getTestCases() != null) {
            for (CiPolicyTestCase t : source.getTestCases()) {
                if (t == null) continue;
                UUID clauseId = t.getClauseId() == null ? null
                        : clauseIds.getOrDefault(t.getClauseId(), t.getClauseId());
                UUID ruleId = t.getRuleCandidateId() == null ? null
                        : ruleIds.getOrDefault(t.getRuleCandidateId(), null);
                // Drop tests tied only to deleted rules
                if (t.getRuleCandidateId() != null && ruleId == null) continue;
                Map<String, Object> tMeta = sanitizePolicyMeta(copyMap(t.getMetadata()), mode);
                tMeta.put("clonedFromTestId", t.getId() == null ? null : t.getId().toString());
                tMeta.remove("lastRun");
                tMeta.remove("lastResult");
                tMeta.remove("passed");
                tMeta.remove("testEvidence");
                target.getTestCases().add(CiPolicyTestCase.builder()
                        .id(UUID.randomUUID())
                        .clauseId(clauseId)
                        .ruleCandidateId(ruleId)
                        .name(t.getName())
                        .inputFacts(copyMap(t.getInputFacts()))
                        .inputMetrics(copyMap(t.getInputMetrics()))
                        .expectedOutcome(t.getExpectedOutcome())
                        .boundaryCase(t.getBoundaryCase())
                        .generatedBy(t.getGeneratedBy())
                        .generationConfidence(t.getGenerationConfidence())
                        .reviewStatus(ReviewState.AI_DRAFTED.name())
                        .reviewedBy(null)
                        .aiExpectedOutcome(t.getAiExpectedOutcome())
                        .approvedAt(null)
                        .metadata(tMeta)
                        .build());
            }
        }

        if (source.getParameters() != null) {
            for (CiPolicyParameter p : source.getParameters()) {
                if (p == null) continue;
                target.getParameters().add(CiPolicyParameter.builder()
                        .id(UUID.randomUUID())
                        .tenantId(targetDocument.getTenantId())
                        .productScope(p.getProductScope())
                        .code(p.getCode())
                        .displayName(p.getDisplayName())
                        .description(p.getDescription())
                        .valueType(p.getValueType())
                        .unit(p.getUnit())
                        .source(p.getSource())
                        .required(p.getRequired())
                        .status(p.getStatus())
                        .version(p.getVersion())
                        .metadata(sanitizePolicyMeta(copyMap(p.getMetadata()), mode))
                        .build());
            }
        }

        if (source.getVocabulary() != null) {
            for (CiPolicyVocabulary v : source.getVocabulary()) {
                if (v == null) continue;
                target.getVocabulary().add(CiPolicyVocabulary.builder()
                        .id(UUID.randomUUID())
                        .tenantId(targetDocument.getTenantId())
                        .scopeLevel(v.getScopeLevel())
                        .productCode(v.getProductCode())
                        .term(v.getTerm())
                        .canonicalMeaning(v.getCanonicalMeaning())
                        .canonicalPath(v.getCanonicalPath())
                        .objectType(v.getObjectType())
                        .synonyms(copyList(v.getSynonyms()))
                        .context(v.getContext())
                        .effectiveFrom(v.getEffectiveFrom() == null ? Instant.now() : v.getEffectiveFrom())
                        .effectiveTo(v.getEffectiveTo())
                        .status(v.getStatus())
                        .version(v.getVersion())
                        .metadata(sanitizePolicyMeta(copyMap(v.getMetadata()), mode))
                        .build());
            }
        }

        for (Map<String, Object> conflict : source.getConflicts()) {
            target.getConflicts().add(copyMap(conflict));
        }

        target.setCompleteness(copyMap(source.getCompleteness()));
        target.setReadiness(copyMap(source.getReadiness()));
        target.setDependencyGraph(copyMap(source.getDependencyGraph()));
        Map<String, Object> preview = copyMap(source.getPreview());
        preview.put("structuredClone", true);
        preview.put("cloneMode", mode.name());
        preview.put("reingested", false);
        target.setPreview(preview);
        // Do not carry draft package / simulation approvals
        target.setDraftPackage(null);
        target.setSimulation(new LinkedHashMap<>());

        // Fresh authoring session — governance reset
        CiPolicyAuthoringSession as = CiPolicyAuthoringSession.builder()
                .id(UUID.randomUUID())
                .tenantId(targetDocument.getTenantId())
                .policyDocumentId(targetDocId)
                .documentVersion(targetDocument.getDocumentVersion())
                .productScope(targetDocument.getProductScope())
                .author(targetDocument.getUploadedBy())
                .status(DocumentStatus.DRAFT_READY.name())
                .startedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .interpretationProviderVersion("STRUCTURED_CLONE_V1")
                .version(0L)
                .metadata(Map.of(
                        "structuredClone", true,
                        "cloneMode", mode.name(),
                        "clonedFromDocumentId", source.getDocument() == null ? null
                                : String.valueOf(source.getDocument().getId())))
                .build();
        target.setAuthoringSession(as);

        // Document metadata: keep policy definitions; drop lifecycle/approvals (caller rewrites lifecycle)
        Map<String, Object> docMeta = sanitizeDocumentMeta(copyMap(source.getDocument() == null
                ? Map.of() : source.getDocument().getMetadata()), mode);
        docMeta.put("structuredClone", true);
        docMeta.put("cloneMode", mode.name());
        docMeta.put("clonedFromDocumentId", source.getDocument() == null ? null
                : String.valueOf(source.getDocument().getId()));
        docMeta.put("reingested", false);
        // Preserve any keys already set on target (e.g. contentHasher from documentService.create)
        Map<String, Object> existing = targetDocument.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(targetDocument.getMetadata());
        existing.putAll(docMeta);
        existing.remove(PolicyLifecycleService.META_KEY);
        targetDocument.setMetadata(existing);

        return target;
    }

    private static boolean isDeleted(CiPolicyRuleCandidate r) {
        Map<String, Object> m = r.getMetadata();
        if (m == null) return false;
        return "DELETED".equalsIgnoreCase(String.valueOf(m.getOrDefault("disposition", "")));
    }

    private static Map<String, Object> sanitizeRuleMeta(Map<String, Object> meta, Mode mode) {
        Map<String, Object> out = sanitizePolicyMeta(meta, mode);
        // Keep dispositions that are policy content (ACCEPTED/EDITED/IGNORED/MANUAL_INPUT/EXTRACTED)
        // Strip temporary test overlays
        out.remove("testValues");
        out.remove("temporaryTestValues");
        out.remove("lastTestResult");
        out.remove("lastTestRun");
        out.remove("testEvidence");
        // Preserve parameterResolutions (policy definitions) — deep-copied already
        Object pr = out.get(ParameterResolutionSupport.META_KEY);
        if (pr instanceof Map<?, ?> raw) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> resRaw)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> res = copyMap((Map<String, Object>) resRaw);
                res.remove("testValue");
                res.remove("temporaryValue");
                res.remove("lastTestValue");
                cleaned.put(String.valueOf(e.getKey()), res);
            }
            out.put(ParameterResolutionSupport.META_KEY, cleaned);
        }
        out.put("structuredClone", true);
        out.put("cloneMode", mode.name());
        return out;
    }

    private static Map<String, Object> sanitizeDocumentMeta(Map<String, Object> meta, Mode mode) {
        Map<String, Object> out = sanitizePolicyMeta(meta, mode);
        out.remove(PolicyLifecycleService.META_KEY);
        out.remove("approvals");
        out.remove("checkerApproval");
        out.remove("cmApproval");
        out.remove("finalApproval");
        out.remove("testApprovals");
        out.remove("simulationApproval");
        out.remove("activationEvidence");
        // Keep policyParameterMappings and measure/calculation adjustments
        return out;
    }

    private static Map<String, Object> sanitizePolicyMeta(Map<String, Object> meta, Mode mode) {
        if (meta == null) return new LinkedHashMap<>();
        Map<String, Object> out = copyMap(meta);
        out.remove("testValues");
        out.remove("temporaryTestValues");
        out.remove("lastTestResult");
        out.remove("lastTestRun");
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> copyMap(Map<String, Object> src) {
        if (src == null) return new LinkedHashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            out.put(e.getKey(), deepCopyValue(e.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> copyList(List<?> src) {
        if (src == null) return new ArrayList<>();
        List<Object> out = new ArrayList<>(src.size());
        for (Object o : src) {
            out.add(deepCopyValue(o));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object v) {
        if (v == null) return null;
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), deepCopyValue(e.getValue()));
            }
            return out;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) out.add(deepCopyValue(o));
            return out;
        }
        return v;
    }

    /** Count underwriting (non-classification) rules — for assertions / messages. */
    public static long countUnderwritingRules(PolicyStudioSession session) {
        if (session == null || session.getRuleCandidates() == null) return 0;
        return session.getRuleCandidates().stream().filter(r -> {
            if (r == null) return false;
            Map<String, Object> expr = r.getExpression();
            if (expr != null && "CLASSIFICATION".equalsIgnoreCase(String.valueOf(expr.get("op")))) {
                return false;
            }
            Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
            String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
            return !sys.startsWith("CLASSIFICATION_");
        }).count();
    }
}
