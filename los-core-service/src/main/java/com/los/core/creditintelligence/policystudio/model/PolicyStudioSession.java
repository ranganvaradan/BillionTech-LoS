package com.los.core.creditintelligence.policystudio.model;

import com.los.core.creditintelligence.policystudio.domain.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate for a Policy Studio processing session. Persistence is via
 * {@link com.los.core.creditintelligence.policystudio.service.PolicyStudioPersistenceService}.
 * Thin cache is optional; reload from store must work after cache clear.
 */
public class PolicyStudioSession {

    private CiPolicyAuthoringSession authoringSession;
    private CiPolicyDocument document;
    private final List<CiPolicyClause> clauses = new ArrayList<>();
    private final List<CiPolicyInterpretation> interpretations = new ArrayList<>();
    private final List<CiPolicyMappingCandidate> mappings = new ArrayList<>();
    private final List<CiPolicyAmbiguity> ambiguities = new ArrayList<>();
    private final List<CiPolicyMetricCandidate> metricCandidates = new ArrayList<>();
    private final List<CiPolicyRuleCandidate> ruleCandidates = new ArrayList<>();
    private final List<CiPolicyTestCase> testCases = new ArrayList<>();
    private final List<CiPolicyReview> reviews = new ArrayList<>();
    private final List<CiPolicyParameter> parameters = new ArrayList<>();
    private final List<CiPolicyVocabulary> vocabulary = new ArrayList<>();
    private final List<Map<String, Object>> conflicts = new ArrayList<>();
    private Map<String, Object> completeness = new LinkedHashMap<>();
    private Map<String, Object> readiness = new LinkedHashMap<>();
    private Map<String, Object> dependencyGraph = new LinkedHashMap<>();
    private Map<String, Object> preview = new LinkedHashMap<>();
    private CiPolicyDraftPackage draftPackage;
    private Map<String, Object> simulation = new LinkedHashMap<>();
    private final List<CiPolicySimulationRun> simulationRuns = new ArrayList<>();
    private final List<CiPolicyDraftDiff> draftDiffs = new ArrayList<>();

    public CiPolicyAuthoringSession getAuthoringSession() { return authoringSession; }
    public void setAuthoringSession(CiPolicyAuthoringSession authoringSession) { this.authoringSession = authoringSession; }
    public CiPolicyDocument getDocument() { return document; }
    public void setDocument(CiPolicyDocument document) { this.document = document; }
    public List<CiPolicyClause> getClauses() { return clauses; }
    public List<CiPolicyInterpretation> getInterpretations() { return interpretations; }
    public List<CiPolicyMappingCandidate> getMappings() { return mappings; }
    public List<CiPolicyAmbiguity> getAmbiguities() { return ambiguities; }
    public List<CiPolicyMetricCandidate> getMetricCandidates() { return metricCandidates; }
    public List<CiPolicyRuleCandidate> getRuleCandidates() { return ruleCandidates; }
    public List<CiPolicyTestCase> getTestCases() { return testCases; }
    public List<CiPolicyReview> getReviews() { return reviews; }
    public List<CiPolicyParameter> getParameters() { return parameters; }
    public List<CiPolicyVocabulary> getVocabulary() { return vocabulary; }
    public List<Map<String, Object>> getConflicts() { return conflicts; }
    public Map<String, Object> getCompleteness() { return completeness; }
    public void setCompleteness(Map<String, Object> completeness) { this.completeness = completeness; }
    public Map<String, Object> getReadiness() { return readiness; }
    public void setReadiness(Map<String, Object> readiness) { this.readiness = readiness; }
    public Map<String, Object> getDependencyGraph() { return dependencyGraph; }
    public void setDependencyGraph(Map<String, Object> dependencyGraph) { this.dependencyGraph = dependencyGraph; }
    public Map<String, Object> getPreview() { return preview; }
    public void setPreview(Map<String, Object> preview) { this.preview = preview; }
    public CiPolicyDraftPackage getDraftPackage() { return draftPackage; }
    public void setDraftPackage(CiPolicyDraftPackage draftPackage) { this.draftPackage = draftPackage; }
    public Map<String, Object> getSimulation() { return simulation; }
    public void setSimulation(Map<String, Object> simulation) { this.simulation = simulation; }
    public List<CiPolicySimulationRun> getSimulationRuns() { return simulationRuns; }
    public List<CiPolicyDraftDiff> getDraftDiffs() { return draftDiffs; }

    public UUID documentId() {
        return document == null ? null : document.getId();
    }

    public UUID sessionId() {
        return authoringSession == null ? null : authoringSession.getId();
    }

    public CiPolicyClause clauseById(UUID id) {
        return clauses.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    public CiPolicyAmbiguity ambiguityById(UUID id) {
        return ambiguities.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
    }

    public CiPolicyRuleCandidate ruleById(UUID id) {
        return ruleCandidates.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    public CiPolicyMetricCandidate metricById(UUID id) {
        return metricCandidates.stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }

    public CiPolicyTestCase testById(UUID id) {
        return testCases.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * @deprecated Use {@link com.los.core.creditintelligence.policystudio.service.PolicyStudioPersistenceService}.
     * Kept as empty map so accidental static references fail soft; orchestrator no longer writes here.
     */
    @Deprecated
    public static final java.util.concurrent.ConcurrentHashMap<UUID, PolicyStudioSession> REGISTRY =
            new java.util.concurrent.ConcurrentHashMap<>();
}
