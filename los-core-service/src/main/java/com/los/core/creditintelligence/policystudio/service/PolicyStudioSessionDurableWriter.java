package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyStudioSessionSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes Policy Studio document + session snapshot in an independent transaction.
 * <p>
 * Isolated from the in-memory {@link PolicyStudioPersistenceService#saveSessionSnapshot}
 * transaction so a JPA failure cannot mark the caller rollback-only
 * ({@code UnexpectedRollbackException} on upload).
 */
@Service
public class PolicyStudioSessionDurableWriter {

    private static final Logger log = LoggerFactory.getLogger(PolicyStudioSessionDurableWriter.class);

    private final CiPolicyDocumentRepository documentRepository;
    private final CiPolicyStudioSessionSnapshotRepository sessionSnapshotRepository;

    public PolicyStudioSessionDurableWriter(
            CiPolicyDocumentRepository documentRepository,
            CiPolicyStudioSessionSnapshotRepository sessionSnapshotRepository) {
        this.documentRepository = documentRepository;
        this.sessionSnapshotRepository = sessionSnapshotRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(PolicyStudioSession session) {
        CiPolicyDocument doc = session.getDocument();
        if (doc == null || doc.getId() == null) {
            throw new IllegalArgumentException("Session document required");
        }
        if (doc.getMetadata() == null) {
            doc.setMetadata(new LinkedHashMap<>());
        } else if (!(doc.getMetadata() instanceof LinkedHashMap)) {
            doc.setMetadata(new LinkedHashMap<>(doc.getMetadata()));
        }
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(Instant.now());
        }
        if (doc.getUploadedAt() == null) {
            doc.setUploadedAt(Instant.now());
        }
        upsertDocument(doc);

        Map<String, Object> payload = PolicyStudioSessionSnapshotCodec.toPayload(session);
        UUID docId = doc.getId();
        CiPolicyStudioSessionSnapshot snap = sessionSnapshotRepository.findById(docId)
                .orElseGet(() -> CiPolicyStudioSessionSnapshot.builder()
                        .policyDocumentId(docId)
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
        log.info("policy-studio durable session committed documentId={} rules={}",
                docId,
                session.getRuleCandidates() == null ? 0 : session.getRuleCandidates().size());
    }

    /**
     * In-memory Policy Studio documents always carry a pre-assigned UUID.
     * {@link CiPolicyDocument} implements {@link org.springframework.data.domain.Persistable}
     * so we can force INSERT vs UPDATE without fighting {@code @GeneratedValue}.
     */
    private void upsertDocument(CiPolicyDocument doc) {
        if (documentRepository.existsById(doc.getId())) {
            doc.markNotNew();
            CiPolicyDocument managed = documentRepository.findById(doc.getId()).orElseThrow();
            managed.setTenantId(doc.getTenantId());
            managed.setLenderId(doc.getLenderId());
            managed.setProductScope(doc.getProductScope());
            managed.setName(doc.getName());
            managed.setDocumentType(doc.getDocumentType());
            managed.setOriginalFileReference(doc.getOriginalFileReference());
            managed.setContentHash(doc.getContentHash());
            managed.setUploadedBy(doc.getUploadedBy());
            managed.setUploadedAt(doc.getUploadedAt());
            managed.setStatus(doc.getStatus());
            managed.setDocumentVersion(doc.getDocumentVersion());
            managed.setLanguage(doc.getLanguage());
            managed.setSourceText(doc.getSourceText());
            managed.setMetadata(doc.getMetadata());
            // SCORECARD-LINKAGE-PROJECTION-INVARIANT-1 — scorecardId and ruleGraphImmutable
            // stay on the durable document row. Session snapshot writes must not clobber them.
            documentRepository.saveAndFlush(managed);
            return;
        }
        doc.markNew();
        documentRepository.saveAndFlush(doc);
    }
}
