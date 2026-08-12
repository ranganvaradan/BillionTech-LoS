package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAuthoringSession;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftDiff;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyParameter;
import com.los.core.creditintelligence.policystudio.domain.CiPolicySimulationRun;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyStudioSessionSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * Persists Policy Studio session snapshots.
 * <p>
 * Working set is an in-memory map. Full session state (rules, expression AST,
 * parameter bindings) is also written to {@code ci_policy_studio_session_snapshot}
 * via JPA when repositories are available, so reload-by-documentId survives JVM
 * restart. Resolution-identity overlays remain on
 * {@link PolicyStudioDurableResolutionStore} for demo reopen / selective invalidation.
 * <p>
 * Note: {@link CiPolicyDocument} alone cannot hold underwriting AST — only source
 * text + metadata. That is why the session-snapshot table exists.
 */
@Service
public class PolicyStudioPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PolicyStudioPersistenceService.class);

    private final ConcurrentHashMap<UUID, PolicyStudioSession> storeByDocumentId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> sessionIdToDocumentId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PolicyStudioSession> cacheByDocumentId = new ConcurrentHashMap<>();
    private final PolicyStudioDurableResolutionStore durableStore;

    /** Optional — present in Spring Boot; null in unit-test constructors. */
    private CiPolicyDocumentRepository documentRepository;
    /** Optional — present in Spring Boot; null in unit-test constructors. */
    private CiPolicyStudioSessionSnapshotRepository sessionSnapshotRepository;
    /** Optional — independent TX writer; null in unit-test constructors. */
    private PolicyStudioSessionDurableWriter durableWriter;

    public PolicyStudioPersistenceService() {
        this(new PolicyStudioDurableResolutionStore("./data/policy-studio-resolutions"));
    }

    @Autowired
    public PolicyStudioPersistenceService(PolicyStudioDurableResolutionStore durableStore) {
        this.durableStore = durableStore != null
                ? durableStore
                : new PolicyStudioDurableResolutionStore("./data/policy-studio-resolutions");
    }

    public PolicyStudioPersistenceService(
            PolicyStudioDurableResolutionStore durableStore,
            CiPolicyDocumentRepository documentRepository,
            CiPolicyStudioSessionSnapshotRepository sessionSnapshotRepository) {
        this(durableStore);
        this.documentRepository = documentRepository;
        this.sessionSnapshotRepository = sessionSnapshotRepository;
    }

    public PolicyStudioPersistenceService(
            PolicyStudioDurableResolutionStore durableStore,
            CiPolicyDocumentRepository documentRepository,
            CiPolicyStudioSessionSnapshotRepository sessionSnapshotRepository,
            PolicyStudioSessionDurableWriter durableWriter) {
        this(durableStore, documentRepository, sessionSnapshotRepository);
        this.durableWriter = durableWriter;
    }

    @Autowired(required = false)
    public void setDocumentRepository(CiPolicyDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Autowired(required = false)
    public void setSessionSnapshotRepository(CiPolicyStudioSessionSnapshotRepository sessionSnapshotRepository) {
        this.sessionSnapshotRepository = sessionSnapshotRepository;
    }

    @Autowired(required = false)
    public void setDurableWriter(PolicyStudioSessionDurableWriter durableWriter) {
        this.durableWriter = durableWriter;
    }

    @Transactional
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
        durableStore.saveFromSession(session);
        persistDurableSession(copy);
    }

    public PolicyStudioSession loadSession(UUID documentId) {
        PolicyStudioSession cached = cacheByDocumentId.get(documentId);
        if (cached != null) {
            return cached;
        }
        PolicyStudioSession stored = storeByDocumentId.get(documentId);
        if (stored == null) {
            stored = loadDurableSession(documentId);
            if (stored == null) {
                return null;
            }
            storeByDocumentId.put(documentId, deepCopy(stored));
            if (stored.getAuthoringSession() != null && stored.getAuthoringSession().getId() != null) {
                sessionIdToDocumentId.put(stored.getAuthoringSession().getId(), documentId);
            }
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

    /**
     * Simulate full process restart for goldens: wipe in-memory maps.
     * Durable resolution overlays on disk remain and are rebound on next demo open.
     * JPA session snapshots (when configured) remain and are rebound on next
     * {@link #loadSession(UUID)} by documentId.
     */
    public void simulateProcessRestart() {
        cacheByDocumentId.clear();
        storeByDocumentId.clear();
        sessionIdToDocumentId.clear();
    }

    public PolicyStudioDurableResolutionStore durableResolutionStore() {
        return durableStore;
    }

    public void clearAllForTests() {
        cacheByDocumentId.clear();
        storeByDocumentId.clear();
        sessionIdToDocumentId.clear();
    }

    /**
     * POLICY-STUDIO-UX-CLOSURE-1 — hard-remove a draft session by document id.
     * Does not cascade to unrelated versions or shared source documents.
     */
    public boolean deleteSession(UUID documentId) {
        if (documentId == null) {
            return false;
        }
        PolicyStudioSession removed = storeByDocumentId.remove(documentId);
        cacheByDocumentId.remove(documentId);
        sessionIdToDocumentId.entrySet().removeIf(e -> documentId.equals(e.getValue()));
        return removed != null;
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

    /**
     * Persist document row + full session payload so reload-by-id survives process restart.
     * No-ops when JPA writer/repositories are unavailable (unit tests).
     * Uses {@link PolicyStudioSessionDurableWriter} (REQUIRES_NEW) so insert failures
     * cannot poison the caller transaction after a pre-assigned-UUID merge miss.
     */
    private void persistDurableSession(PolicyStudioSession session) {
        if (durableWriter == null
                && (documentRepository == null || sessionSnapshotRepository == null)) {
            log.debug("policy-studio durable session skip — JPA repositories unavailable");
            return;
        }
        try {
            log.info("policy-studio durable session save documentId={} rules={}",
                    session.documentId(),
                    session.getRuleCandidates() == null ? 0 : session.getRuleCandidates().size());
            if (durableWriter != null) {
                durableWriter.write(session);
                return;
            }
            // Fallback for tests that inject repos without the writer bean
            CiPolicyDocument doc = session.getDocument();
            if (doc.getMetadata() == null) {
                doc.setMetadata(new LinkedHashMap<>());
            } else if (!(doc.getMetadata() instanceof LinkedHashMap)) {
                doc.setMetadata(new LinkedHashMap<>(doc.getMetadata()));
            }
            documentRepository.saveAndFlush(doc);
            Map<String, Object> payload = PolicyStudioSessionSnapshotCodec.toPayload(session);
            CiPolicyStudioSessionSnapshot snap = sessionSnapshotRepository.findById(doc.getId())
                    .orElseGet(() -> CiPolicyStudioSessionSnapshot.builder()
                            .policyDocumentId(doc.getId())
                            .tenantId(doc.getTenantId())
                            .createdAt(Instant.now())
                            .build());
            snap.setTenantId(doc.getTenantId());
            snap.setPayload(payload);
            snap.setContentHash(doc.getContentHash());
            snap.setUpdatedAt(Instant.now());
            if (snap.getCreatedAt() == null) {
                snap.setCreatedAt(Instant.now());
            }
            sessionSnapshotRepository.saveAndFlush(snap);
        } catch (Exception e) {
            log.warn("policy-studio durable session save failed documentId={} reason={}",
                    session.documentId(), e.toString(), e);
        }
    }

    private PolicyStudioSession loadDurableSession(UUID documentId) {
        if (sessionSnapshotRepository == null || documentId == null) {
            return null;
        }
        try {
            Optional<CiPolicyStudioSessionSnapshot> opt = sessionSnapshotRepository.findById(documentId);
            if (opt.isEmpty() || opt.get().getPayload() == null || opt.get().getPayload().isEmpty()) {
                return null;
            }
            PolicyStudioSession session = PolicyStudioSessionSnapshotCodec.fromPayload(opt.get().getPayload());
            if (session != null && session.getDocument() != null) {
                log.info("policy-studio durable session rebound documentId={} rules={}",
                        documentId,
                        session.getRuleCandidates() == null ? 0 : session.getRuleCandidates().size());
            }
            return session;
        } catch (Exception e) {
            log.warn("policy-studio durable session load failed documentId={} reason={}",
                    documentId, e.toString(), e);
            return null;
        }
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
        dst.setDocument(copyDocument(src.getDocument()));
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

    private com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument copyDocument(
            com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument src) {
        if (src == null) return null;
        return com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument.builder()
                .id(src.getId())
                .tenantId(src.getTenantId())
                .lenderId(src.getLenderId())
                .productScope(src.getProductScope())
                .name(src.getName())
                .documentType(src.getDocumentType())
                .originalFileReference(src.getOriginalFileReference())
                .contentHash(src.getContentHash())
                .uploadedBy(src.getUploadedBy())
                .uploadedAt(src.getUploadedAt())
                .status(src.getStatus())
                .documentVersion(src.getDocumentVersion())
                .language(src.getLanguage())
                .sourceText(src.getSourceText())
                .metadata(copyMap(src.getMetadata()))
                .version(src.getVersion())
                .createdAt(src.getCreatedAt())
                .build();
    }

    private Map<String, Object> copyMap(Map<String, Object> m) {
        if (m == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(m);
    }
}
