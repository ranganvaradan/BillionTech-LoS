package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraph;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyScopeSupport;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyStudioSessionSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * POLICY-STUDIO-DURABLE-LANDING-LIST-1 — Policy Studio landing membership authority.
 * <p>
 * Membership = durable {@code ci_policy_document} rows (plus in-memory-only drafts when
 * JPA is unavailable in unit tests). Applicability and in-memory sessions enrich rows
 * but never gate visibility.
 */
@Slf4j
@Service
public class PolicyStudioDurableLandingListService {

    private CiPolicyDocumentRepository documentRepository;
    private CiPolicyStudioSessionSnapshotRepository snapshotRepository;
    private CiPolicyApplicabilityRepository applicabilityRepository;
    private CiPolicyRuleGraphRepository ruleGraphRepository;

    public PolicyStudioDurableLandingListService() {
    }

    @Autowired(required = false)
    public void setDocumentRepository(CiPolicyDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Autowired(required = false)
    public void setSnapshotRepository(CiPolicyStudioSessionSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Autowired(required = false)
    public void setApplicabilityRepository(CiPolicyApplicabilityRepository applicabilityRepository) {
        this.applicabilityRepository = applicabilityRepository;
    }

    @Autowired(required = false)
    public void setRuleGraphRepository(CiPolicyRuleGraphRepository ruleGraphRepository) {
        this.ruleGraphRepository = ruleGraphRepository;
    }

    /**
     * Build landing rows for a tenant. Does not hydrate durable drafts into the
     * in-memory session store.
     */
    public List<Map<String, Object>> list(
            UUID tenantId,
            PolicyStudioPersistenceService persistence,
            PolicyLifecycleService lifecycleService,
            Map<UUID, Map<String, Object>> sessionMeta) {
        Map<UUID, PolicyStudioSession> memorySessions = indexMemorySessions(persistence, tenantId);
        LinkedHashMap<UUID, CiPolicyDocument> membership = new LinkedHashMap<>();

        if (documentRepository != null && tenantId != null) {
            for (CiPolicyDocument doc : documentRepository.findByTenantIdOrderByUploadedAtDesc(tenantId)) {
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                if (!tenantId.equals(doc.getTenantId())) {
                    continue;
                }
                membership.put(doc.getId(), doc);
            }
        }

        // Unit-test / pre-persist path: in-memory drafts without JPA still appear.
        for (Map.Entry<UUID, PolicyStudioSession> e : memorySessions.entrySet()) {
            membership.putIfAbsent(e.getKey(), e.getValue().getDocument());
        }

        Map<UUID, CiPolicyStudioSessionSnapshot> snapshots = loadSnapshots(membership.keySet());
        Map<UUID, CiPolicyApplicability> applicabilityByDoc = loadApplicability(tenantId);
        Map<UUID, CiPolicyRuleGraph> graphs = loadGraphs(membership.keySet());

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (Map.Entry<UUID, CiPolicyDocument> e : membership.entrySet()) {
            UUID docId = e.getKey();
            if (!seen.add(docId)) {
                continue;
            }
            rows.add(buildRow(
                    docId,
                    e.getValue(),
                    memorySessions.get(docId),
                    snapshots.get(docId),
                    applicabilityByDoc.get(docId),
                    graphs.get(docId),
                    sessionMeta == null ? Map.of() : sessionMeta.getOrDefault(docId, Map.of()),
                    lifecycleService));
        }
        rows.sort((a, b) -> String.valueOf(b.get("policyName"))
                .compareToIgnoreCase(String.valueOf(a.get("policyName"))));
        return rows;
    }

    private Map<String, Object> buildRow(
            UUID docId,
            CiPolicyDocument document,
            PolicyStudioSession memorySession,
            CiPolicyStudioSessionSnapshot snapshot,
            CiPolicyApplicability applicability,
            CiPolicyRuleGraph graph,
            Map<String, Object> meta,
            PolicyLifecycleService lifecycleService) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("documentId", docId.toString());
        row.put("policyName", document == null || document.getName() == null
                ? "(unnamed policy)" : document.getName());

        Integer docVersion = document == null ? null : document.getDocumentVersion();
        row.put("documentVersion", docVersion == null ? 1 : docVersion);
        row.put("policyVersion", "v" + (docVersion == null ? 1 : docVersion));

        String docStatus = document == null || document.getStatus() == null
                ? "DRAFT" : document.getStatus();
        String lifecycleStatus = null;
        String approvalStatus = null;
        if (memorySession != null && lifecycleService != null) {
            try {
                Map<String, Object> life = lifecycleService.settingsView(memorySession);
                Object biz = life.get("businessStatus");
                if (biz != null && !String.valueOf(biz).isBlank()) {
                    lifecycleStatus = String.valueOf(biz);
                }
            } catch (Exception ignored) {
                // keep document status
            }
        }
        if (lifecycleStatus == null && document != null && document.getMetadata() != null) {
            Object life = document.getMetadata().get(PolicyLifecycleService.META_KEY);
            if (life instanceof Map<?, ?> m && m.get("businessStatus") != null
                    && !String.valueOf(m.get("businessStatus")).isBlank()) {
                lifecycleStatus = String.valueOf(m.get("businessStatus"));
            }
        }
        if (applicability != null && applicability.getBusinessStatus() != null
                && !applicability.getBusinessStatus().isBlank()) {
            approvalStatus = applicability.getBusinessStatus();
        }
        String status = lifecycleStatus != null ? lifecycleStatus
                : (approvalStatus != null ? approvalStatus : docStatus);
        row.put("status", status);
        row.put("lifecycleStatus", lifecycleStatus != null ? lifecycleStatus : status);
        row.put("approvalStatus", approvalStatus);
        row.put("statusMeaning", "POLICY_VERSION_LIFECYCLE");
        row.put("statusAxis", "POLICY_VERSION_LIFECYCLE");

        if (applicability != null) {
            row.put("applicabilityId", applicability.getId() == null
                    ? null : applicability.getId().toString());
            row.put("products", applicability.getProducts() == null
                    ? List.of() : applicability.getProducts());
            row.put("effectiveFrom", applicability.getEffectiveFrom());
            Map<String, Object> appMap = applicabilityToMap(applicability);
            Object summary = PolicyScopeSupport.summarize(appMap);
            row.put("scopeSummary", summary);
            if (applicability.getPolicyVersionLabel() != null
                    && !applicability.getPolicyVersionLabel().isBlank()) {
                row.put("policyVersion", applicability.getPolicyVersionLabel());
            }
        } else {
            row.put("products", List.of());
            row.put("effectiveFrom", null);
            row.put("scopeSummary", Map.of(
                    "label", "Not yet configured",
                    "detail", "Draft scope not published"));
        }

        PolicyStudioSession enrichSession = memorySession;
        boolean hydratedFromSnapshot = false;
        if (enrichSession == null && snapshot != null && snapshot.getPayload() != null
                && !snapshot.getPayload().isEmpty()) {
            enrichSession = PolicyStudioSessionSnapshotCodec.fromPayload(snapshot.getPayload());
            hydratedFromSnapshot = enrichSession != null;
        }

        long ruleCount = 0;
        long needsInput = 0;
        if (enrichSession != null) {
            ruleCount = countUnderwritingRules(enrichSession);
            needsInput = PolicyExecutionReadiness.countNeedsBusinessInput(enrichSession);
        } else if (graph != null) {
            ruleCount = graph.getRuleCount();
        }
        row.put("underwritingRuleCount", ruleCount);
        row.put("needsInputCount", needsInput);
        row.put("needsInputMeaning", "CURRENT_STRUCTURAL_PARAMETER_BLOCKERS");
        row.put("needsInputAuthority",
                "PolicyRuleParticipation+PolicyExecutionReadiness.currentParameterBlockers");

        List<String> landingActions = List.of("OPEN", "COPY");
        if (lifecycleService != null) {
            try {
                if (memorySession != null) {
                    landingActions = lifecycleService.landingActions(memorySession);
                } else {
                    Map<String, Object> catalogueLike = new LinkedHashMap<>(row);
                    if (applicability != null && applicability.getId() != null) {
                        catalogueLike.put("applicabilityId", applicability.getId().toString());
                    }
                    landingActions = lifecycleService.landingActionsForCatalogueRow(catalogueLike);
                }
            } catch (Exception ignored) {
                // keep Open/Copy
            }
        }
        row.put("availableActions", landingActions);

        boolean sessionExists = memorySession != null;
        row.put("sessionExists", sessionExists);
        row.put("kind", sessionExists
                ? meta.getOrDefault("kind", Boolean.TRUE.equals(meta.get("demo")) ? "demo" : "session")
                : (applicability != null ? "catalogue" : "durable"));
        row.put("demo", Boolean.TRUE.equals(meta.get("demo")));
        row.put("copiedFromLabel", meta.get("copiedFromLabel"));
        row.put("membershipAuthority", "DURABLE_DOCUMENT");
        row.put("enrichmentSource", sessionExists
                ? "SESSION"
                : (hydratedFromSnapshot ? "DURABLE_SNAPSHOT" : (applicability != null ? "APPLICABILITY" : "DOCUMENT")));

        Instant lastUpdated = null;
        if (snapshot != null && snapshot.getUpdatedAt() != null) {
            lastUpdated = snapshot.getUpdatedAt();
        } else if (applicability != null && applicability.getUpdatedAt() != null) {
            lastUpdated = applicability.getUpdatedAt();
        } else if (document != null && document.getUploadedAt() != null) {
            lastUpdated = document.getUploadedAt();
        } else if (document != null && document.getCreatedAt() != null) {
            lastUpdated = document.getCreatedAt();
        }
        row.put("lastUpdated", lastUpdated == null ? null : lastUpdated.toString());
        return row;
    }

    private static long countUnderwritingRules(PolicyStudioSession session) {
        if (session.getRuleCandidates() == null) {
            return 0;
        }
        return session.getRuleCandidates().stream()
                .filter(r -> r.getSystemRuleId() == null
                        || !r.getSystemRuleId().toUpperCase(Locale.ROOT).contains("OVERDUE_CHILD"))
                .count();
    }

    private static Map<UUID, PolicyStudioSession> indexMemorySessions(
            PolicyStudioPersistenceService persistence, UUID tenantId) {
        Map<UUID, PolicyStudioSession> out = new LinkedHashMap<>();
        if (persistence == null) {
            return out;
        }
        for (PolicyStudioSession session : persistence.listAllSessions()) {
            if (session == null || session.getDocument() == null || session.getDocument().getId() == null) {
                continue;
            }
            if (tenantId != null && session.getDocument().getTenantId() != null
                    && !tenantId.equals(session.getDocument().getTenantId())) {
                continue;
            }
            out.put(session.getDocument().getId(), session);
        }
        return out;
    }

    private Map<UUID, CiPolicyStudioSessionSnapshot> loadSnapshots(Collection<UUID> ids) {
        Map<UUID, CiPolicyStudioSessionSnapshot> out = new HashMap<>();
        if (snapshotRepository == null || ids == null || ids.isEmpty()) {
            return out;
        }
        try {
            for (CiPolicyStudioSessionSnapshot snap : snapshotRepository.findAllById(ids)) {
                if (snap != null && snap.getPolicyDocumentId() != null) {
                    out.put(snap.getPolicyDocumentId(), snap);
                }
            }
        } catch (Exception e) {
            log.warn("durable landing snapshot load skipped reason={}", e.getClass().getSimpleName());
        }
        return out;
    }

    private Map<UUID, CiPolicyApplicability> loadApplicability(UUID tenantId) {
        Map<UUID, CiPolicyApplicability> out = new LinkedHashMap<>();
        if (applicabilityRepository == null || tenantId == null) {
            return out;
        }
        try {
            for (CiPolicyApplicability a : applicabilityRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)) {
                if (a == null || a.getPolicyDocumentId() == null) {
                    continue;
                }
                // First row wins (newest updated_at) — do not hide older docs.
                out.putIfAbsent(a.getPolicyDocumentId(), a);
            }
        } catch (Exception e) {
            log.warn("durable landing applicability load skipped reason={}", e.getClass().getSimpleName());
        }
        return out;
    }

    private Map<UUID, CiPolicyRuleGraph> loadGraphs(Collection<UUID> ids) {
        Map<UUID, CiPolicyRuleGraph> out = new HashMap<>();
        if (ruleGraphRepository == null || ids == null || ids.isEmpty()) {
            return out;
        }
        try {
            for (UUID id : ids) {
                ruleGraphRepository.findFirstByPolicyDocumentIdOrderByDocumentVersionDesc(id)
                        .ifPresent(g -> out.put(id, g));
            }
        } catch (Exception e) {
            log.warn("durable landing graph load skipped reason={}", e.getClass().getSimpleName());
        }
        return out;
    }

    private static Map<String, Object> applicabilityToMap(CiPolicyApplicability a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("products", a.getProducts() == null ? List.of() : a.getProducts());
        m.put("facilityType", a.getFacilityType());
        m.put("customerSegment", a.getCustomerSegment());
        m.put("borrowerType", a.getBorrowerType());
        m.put("borrowerTypes", a.getBorrowerTypes() == null ? List.of() : a.getBorrowerTypes());
        m.put("securedUnsecured", a.getSecuredUnsecured());
        m.put("programScheme", a.getProgramScheme());
        m.put("minLoanAmount", a.getMinLoanAmount());
        m.put("maxLoanAmount", a.getMaxLoanAmount());
        m.put("effectiveFrom", a.getEffectiveFrom());
        m.put("effectiveUntil", a.getEffectiveUntil());
        return m;
    }

    /** Test helper — build rows from explicit fixtures without Spring repos. */
    public List<Map<String, Object>> listFromFixtures(
            UUID tenantId,
            List<CiPolicyDocument> documents,
            List<CiPolicyStudioSessionSnapshot> snapshots,
            List<CiPolicyApplicability> applicabilityRows,
            List<CiPolicyRuleGraph> graphs,
            Map<UUID, PolicyStudioSession> memorySessions,
            PolicyLifecycleService lifecycleService,
            Map<UUID, Map<String, Object>> sessionMeta) {
        Objects.requireNonNull(tenantId, "tenantId");
        LinkedHashMap<UUID, CiPolicyDocument> membership = new LinkedHashMap<>();
        if (documents != null) {
            for (CiPolicyDocument doc : documents) {
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                if (!tenantId.equals(doc.getTenantId())) {
                    continue;
                }
                membership.put(doc.getId(), doc);
            }
        }
        if (memorySessions != null) {
            for (Map.Entry<UUID, PolicyStudioSession> e : memorySessions.entrySet()) {
                if (e.getValue() != null && e.getValue().getDocument() != null) {
                    membership.putIfAbsent(e.getKey(), e.getValue().getDocument());
                }
            }
        }
        Map<UUID, CiPolicyStudioSessionSnapshot> snapMap = new HashMap<>();
        if (snapshots != null) {
            for (CiPolicyStudioSessionSnapshot s : snapshots) {
                if (s != null && s.getPolicyDocumentId() != null) {
                    snapMap.put(s.getPolicyDocumentId(), s);
                }
            }
        }
        Map<UUID, CiPolicyApplicability> appMap = new LinkedHashMap<>();
        if (applicabilityRows != null) {
            for (CiPolicyApplicability a : applicabilityRows) {
                if (a != null && a.getPolicyDocumentId() != null) {
                    appMap.putIfAbsent(a.getPolicyDocumentId(), a);
                }
            }
        }
        Map<UUID, CiPolicyRuleGraph> graphMap = new HashMap<>();
        if (graphs != null) {
            for (CiPolicyRuleGraph g : graphs) {
                if (g != null && g.getPolicyDocumentId() != null) {
                    graphMap.putIfAbsent(g.getPolicyDocumentId(), g);
                }
            }
        }
        Map<UUID, PolicyStudioSession> mem = memorySessions == null ? Map.of() : memorySessions;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<UUID, CiPolicyDocument> e : membership.entrySet()) {
            UUID docId = e.getKey();
            rows.add(buildRow(
                    docId,
                    e.getValue(),
                    mem.get(docId),
                    snapMap.get(docId),
                    appMap.get(docId),
                    graphMap.get(docId),
                    sessionMeta == null ? Map.of() : sessionMeta.getOrDefault(docId, Map.of()),
                    lifecycleService));
        }
        rows.sort((a, b) -> String.valueOf(b.get("policyName"))
                .compareToIgnoreCase(String.valueOf(a.get("policyName"))));
        return rows;
    }
}
