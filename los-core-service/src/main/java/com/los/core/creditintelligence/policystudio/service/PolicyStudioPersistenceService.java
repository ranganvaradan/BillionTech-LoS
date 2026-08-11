package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAuthoringSession;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftDiff;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyParameter;
import com.los.core.creditintelligence.policystudio.domain.CiPolicySimulationRun;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists Policy Studio session snapshots. Uses an in-memory durable store for unit tests
 * and when JPA repos are unavailable; thin cache can be cleared to simulate process restart.
 */
@Service
public class PolicyStudioPersistenceService {

    /** Durable store (survives cache clear — restart simulation). */
    private final ConcurrentHashMap<UUID, PolicyStudioSession> storeByDocumentId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> sessionIdToDocumentId = new ConcurrentHashMap<>();

    /** Optional thin cache; clear to force reload from store. */
    private final ConcurrentHashMap<UUID, PolicyStudioSession> cacheByDocumentId = new ConcurrentHashMap<>();

    public void saveSessionSnapshot(PolicyStudioSession session) {
        if (session == null || session.getDocument() == null || session.getDocument().getId() == null) {
            throw new IllegalArgumentException("Session document required");
        }
        ensureAuthoringSession(session);
        session.getAuthoringSession().setLastUpdatedAt(Instant.now());
        if (session.getReadiness() != null && session.getReadiness().get("score") != null) {
            session.getAuthoringSession().setReadinessScore(
                    new BigDecimal(String.valueOf(session.getReadiness().get("score"))));
            session.getAuthoringSession().setReadinessGrade(
                    String.valueOf(session.getReadiness().getOrDefault("grade", "DRAFT")));
        }
        PolicyStudioSession copy = deepCopy(session);
        UUID docId = copy.getDocument().getId();
        storeByDocumentId.put(docId, copy);
        if (copy.getAuthoringSession() != null && copy.getAuthoringSession().getId() != null) {
            sessionIdToDocumentId.put(copy.getAuthoringSession().getId(), docId);
        }
        cacheByDocumentId.put(docId, session);
    }

    public PolicyStudioSession loadSession(UUID documentId) {
        PolicyStudioSession cached = cacheByDocumentId.get(documentId);
        if (cached != null) {
            return cached;
        }
        PolicyStudioSession stored = storeByDocumentId.get(documentId);
        if (stored == null) {
            return null;
        }
        PolicyStudioSession reloaded = deepCopy(stored);
        cacheByDocumentId.put(documentId, reloaded);
        return reloaded;
    }

    public PolicyStudioSession loadSessionBySessionId(UUID sessionId) {
        UUID docId = sessionIdToDocumentId.get(sessionId);
        if (docId == null) {
            return null;
        }
        return loadSession(docId);
    }

    public PolicyStudioSession requireSession(UUID documentId) {
        PolicyStudioSession s = loadSession(documentId);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document session not found");
        }
        return s;
    }

    public PolicyStudioSession requireSessionForTenant(UUID documentId, UUID tenantId) {
        PolicyStudioSession s = requireSession(documentId);
        if (tenantId != null && s.getDocument() != null
                && !tenantId.equals(s.getDocument().getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant isolation violation");
        }
        return s;
    }

    /** Simulate JVM restart: drop thin cache; durable store remains. */
    public void clearCache() {
        cacheByDocumentId.clear();
    }

    public void clearAllForTests() {
        cacheByDocumentId.clear();
        storeByDocumentId.clear();
        sessionIdToDocumentId.clear();
    }

    /** POLICY-CREATION-1 — list in-memory sessions for Credit Policies landing. */
    public List<PolicyStudioSession> listAllSessions() {
        return new ArrayList<>(storeByDocumentId.values());
    }

    public Optional<CiPolicyParameter> findParameter(UUID tenantId, String code) {
        for (PolicyStudioSession s : storeByDocumentId.values()) {
            if (s.getDocument() != null && tenantId.equals(s.getDocument().getTenantId())) {
                for (CiPolicyParameter p : s.getParameters()) {
                    if (code.equals(p.getCode())) {
                        return Optional.of(p);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public void saveParameter(PolicyStudioSession session, CiPolicyParameter parameter) {
        session.getParameters().removeIf(p -> p.getCode().equals(parameter.getCode())
                && (p.getProductScope() == null ? parameter.getProductScope() == null
                : p.getProductScope().equals(parameter.getProductScope())));
        session.getParameters().add(parameter);
        saveSessionSnapshot(session);
    }

    public void saveVocabulary(PolicyStudioSession session, CiPolicyVocabulary vocab) {
        session.getVocabulary().add(vocab);
        saveSessionSnapshot(session);
    }

    public void saveSimulationRun(PolicyStudioSession session, CiPolicySimulationRun run) {
        session.getSimulationRuns().add(run);
        saveSessionSnapshot(session);
    }

    public void saveDraftDiff(PolicyStudioSession session, CiPolicyDraftDiff diff) {
        session.getDraftDiffs().add(diff);
        saveSessionSnapshot(session);
    }

    public void bumpOptimisticVersion(PolicyStudioSession session, Long expectedVersion) {
        CiPolicyAuthoringSession as = session.getAuthoringSession();
        if (as == null) {
            ensureAuthoringSession(session);
            as = session.getAuthoringSession();
        }
        if (expectedVersion != null && as.getVersion() != null
                && !as.getVersion().equals(expectedVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Optimistic lock conflict: session version mismatch");
        }
        as.setVersion(as.getVersion() == null ? 1L : as.getVersion() + 1);
        saveSessionSnapshot(session);
    }

    private void ensureAuthoringSession(PolicyStudioSession session) {
        if (session.getAuthoringSession() != null) {
            return;
        }
        CiPolicyAuthoringSession as = CiPolicyAuthoringSession.builder()
                .id(UUID.randomUUID())
                .tenantId(session.getDocument().getTenantId())
                .policyDocumentId(session.getDocument().getId())
                .documentVersion(session.getDocument().getDocumentVersion())
                .author(session.getDocument().getUploadedBy())
                .status(session.getDocument().getStatus())
                .interpretationProviderVersion("DETERMINISTIC_GOLDEN_V1")
                .version(0L)
                .metadata(Map.of())
                .build();
        session.setAuthoringSession(as);
    }

    @SuppressWarnings("unchecked")
    private PolicyStudioSession deepCopy(PolicyStudioSession src) {
        PolicyStudioSession dst = new PolicyStudioSession();
        if (src.getAuthoringSession() != null) {
            CiPolicyAuthoringSession a = src.getAuthoringSession();
            dst.setAuthoringSession(CiPolicyAuthoringSession.builder()
                    .id(a.getId())
                    .tenantId(a.getTenantId())
                    .policyDocumentId(a.getPolicyDocumentId())
                    .documentVersion(a.getDocumentVersion())
                    .productScope(a.getProductScope())
                    .author(a.getAuthor())
                    .assignedCreditManager(a.getAssignedCreditManager())
                    .assignedChecker(a.getAssignedChecker())
                    .status(a.getStatus())
                    .startedAt(a.getStartedAt())
                    .lastUpdatedAt(a.getLastUpdatedAt())
                    .completedAt(a.getCompletedAt())
                    .registryVersion(a.getRegistryVersion())
                    .interpretationProviderVersion(a.getInterpretationProviderVersion())
                    .dslVersion(a.getDslVersion())
                    .readinessScore(a.getReadinessScore())
                    .readinessGrade(a.getReadinessGrade())
                    .version(a.getVersion())
                    .metadata(copyMap(a.getMetadata()))
                    .createdAt(a.getCreatedAt())
                    .build());
        }
        dst.setDocument(src.getDocument());
        dst.getClauses().addAll(src.getClauses());
        dst.getInterpretations().addAll(src.getInterpretations());
        dst.getMappings().addAll(src.getMappings());
        dst.getAmbiguities().addAll(new ArrayList<>(src.getAmbiguities()));
        dst.getMetricCandidates().addAll(src.getMetricCandidates());
        dst.getRuleCandidates().addAll(src.getRuleCandidates());
        dst.getTestCases().addAll(src.getTestCases());
        dst.getReviews().addAll(src.getReviews());
        dst.getParameters().addAll(new ArrayList<>(src.getParameters()));
        dst.getVocabulary().addAll(new ArrayList<>(src.getVocabulary()));
        dst.getConflicts().addAll(new ArrayList<>(src.getConflicts()));
        dst.setCompleteness(copyMap(src.getCompleteness()));
        dst.setReadiness(copyMap(src.getReadiness()));
        dst.setDependencyGraph(copyMap(src.getDependencyGraph()));
        dst.setPreview(copyMap(src.getPreview()));
        dst.setDraftPackage(src.getDraftPackage());
        dst.setSimulation(copyMap(src.getSimulation()));
        dst.getSimulationRuns().addAll(new ArrayList<>(src.getSimulationRuns()));
        dst.getDraftDiffs().addAll(new ArrayList<>(src.getDraftDiffs()));
        return dst;
    }

    private Map<String, Object> copyMap(Map<String, Object> m) {
        if (m == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(m);
    }
}
