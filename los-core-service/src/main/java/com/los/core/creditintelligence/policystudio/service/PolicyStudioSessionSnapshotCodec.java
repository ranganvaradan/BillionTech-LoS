package com.los.core.creditintelligence.policystudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAuthoringSession;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftDiff;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMappingCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyParameter;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyReview;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicySimulationRun;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialize / rehydrate {@link PolicyStudioSession} for durable DB snapshots.
 */
final class PolicyStudioSessionSnapshotCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PolicyStudioSessionSnapshotCodec() {}

    static Map<String, Object> toPayload(PolicyStudioSession session) {
        if (session == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("document", session.getDocument());
        payload.put("authoringSession", session.getAuthoringSession());
        payload.put("clauses", session.getClauses());
        payload.put("interpretations", session.getInterpretations());
        payload.put("mappings", session.getMappings());
        payload.put("ambiguities", session.getAmbiguities());
        payload.put("metricCandidates", session.getMetricCandidates());
        payload.put("ruleCandidates", session.getRuleCandidates());
        payload.put("testCases", session.getTestCases());
        payload.put("reviews", session.getReviews());
        payload.put("parameters", session.getParameters());
        payload.put("vocabulary", session.getVocabulary());
        payload.put("conflicts", session.getConflicts());
        payload.put("completeness", session.getCompleteness());
        payload.put("readiness", session.getReadiness());
        payload.put("dependencyGraph", session.getDependencyGraph());
        payload.put("preview", session.getPreview());
        payload.put("draftPackage", session.getDraftPackage());
        payload.put("simulation", session.getSimulation());
        payload.put("simulationRuns", session.getSimulationRuns());
        payload.put("draftDiffs", session.getDraftDiffs());
        // Round-trip via JSON so payload is plain Map/List suitable for jsonb
        return MAPPER.convertValue(payload, Map.class);
    }

    @SuppressWarnings("unchecked")
    static PolicyStudioSession fromPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(convert(payload.get("document"), CiPolicyDocument.class));
        session.setAuthoringSession(convert(payload.get("authoringSession"), CiPolicyAuthoringSession.class));
        session.getClauses().addAll(convertList(payload.get("clauses"), CiPolicyClause.class));
        session.getInterpretations().addAll(convertList(payload.get("interpretations"), CiPolicyInterpretation.class));
        session.getMappings().addAll(convertList(payload.get("mappings"), CiPolicyMappingCandidate.class));
        session.getAmbiguities().addAll(convertList(payload.get("ambiguities"), CiPolicyAmbiguity.class));
        session.getMetricCandidates().addAll(convertList(payload.get("metricCandidates"), CiPolicyMetricCandidate.class));
        session.getRuleCandidates().addAll(convertList(payload.get("ruleCandidates"), CiPolicyRuleCandidate.class));
        session.getTestCases().addAll(convertList(payload.get("testCases"), CiPolicyTestCase.class));
        session.getReviews().addAll(convertList(payload.get("reviews"), CiPolicyReview.class));
        session.getParameters().addAll(convertList(payload.get("parameters"), CiPolicyParameter.class));
        session.getVocabulary().addAll(convertList(payload.get("vocabulary"), CiPolicyVocabulary.class));
        Object conflicts = payload.get("conflicts");
        if (conflicts instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    session.getConflicts().add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        session.setCompleteness(asMap(payload.get("completeness")));
        session.setReadiness(asMap(payload.get("readiness")));
        session.setDependencyGraph(asMap(payload.get("dependencyGraph")));
        session.setPreview(asMap(payload.get("preview")));
        session.setDraftPackage(convert(payload.get("draftPackage"), CiPolicyDraftPackage.class));
        session.setSimulation(asMap(payload.get("simulation")));
        session.getSimulationRuns().addAll(convertList(payload.get("simulationRuns"), CiPolicySimulationRun.class));
        session.getDraftDiffs().addAll(convertList(payload.get("draftDiffs"), CiPolicyDraftDiff.class));
        return session;
    }

    private static <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return MAPPER.convertValue(value, type);
    }

    private static <T> List<T> convertList(Object value, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (o != null) {
                out.add(MAPPER.convertValue(o, type));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }
}
