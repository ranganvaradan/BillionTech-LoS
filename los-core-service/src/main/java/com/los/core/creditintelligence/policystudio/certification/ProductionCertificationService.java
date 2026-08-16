package com.los.core.creditintelligence.policystudio.certification;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Wave-8 single Production Certification authority.
 * Explicit certify/revoke only — never auto-certify from tests/capability/ACTIVE/production_ready.
 */
@Service
public class ProductionCertificationService {

    public static final String AUTHORITY = "ProductionCertificationService";

    private final InMemoryProductionCertificationLedger ledger;

    public ProductionCertificationService() {
        this(new InMemoryProductionCertificationLedger());
    }

    public ProductionCertificationService(InMemoryProductionCertificationLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger);
    }

    public InMemoryProductionCertificationLedger ledger() {
        return ledger;
    }

    public CertificationStatus getCertificationStatus(
            CertifiableArtifactType type, String artifactId, String version,
            CertificationScopeType scopeType, String scopeId) {
        return resolveEffective(type, artifactId, version, scopeType, scopeId)
                .map(CertificationRecord::status)
                .orElse(CertificationStatus.UNCERTIFIED);
    }

    public boolean isCertifiedForLiveUse(
            CertifiableArtifactType type, String artifactId, String version,
            CertificationScopeType scopeType, String scopeId) {
        return resolveEffective(type, artifactId, version, scopeType, scopeId)
                .map(r -> r.permitsLiveUse(Instant.now()))
                .orElse(false);
    }

    /**
     * Resolve TENANT (or PRODUCT/PROGRAM) first, then PLATFORM. No cross-tenant leakage.
     */
    public Optional<CertificationRecord> resolveEffective(
            CertifiableArtifactType type, String artifactId, String version,
            CertificationScopeType scopeType, String scopeId) {
        if (scopeType != null && scopeType != CertificationScopeType.PLATFORM && scopeId != null) {
            Optional<CertificationRecord> scoped = ledger.find(type, artifactId, version, scopeType, scopeId);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return ledger.find(type, artifactId, version, CertificationScopeType.PLATFORM, null);
    }

    public CertificationRecord certify(
            CertifiableArtifactType type,
            String artifactId,
            String version,
            CertificationScopeType scopeType,
            String scopeId,
            String certifiedBy,
            String evidenceSummary,
            Map<String, Object> evidenceJson,
            String semanticCatalogueVersion,
            String engineVersion,
            String producerVersion) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(certifiedBy, "certifiedBy");
        if (certifiedBy.isBlank()) {
            throw new IllegalArgumentException("certifiedBy required — no automatic certification");
        }
        CertificationScopeType scope = scopeType == null ? CertificationScopeType.PLATFORM : scopeType;
        Instant now = Instant.now();
        Optional<CertificationRecord> existing = ledger.find(type, artifactId, version, scope, scopeId);
        UUID id = existing.map(CertificationRecord::certificationId).orElse(UUID.randomUUID());
        CertificationStatus from = existing.map(CertificationRecord::status).orElse(CertificationStatus.UNCERTIFIED);
        CertificationRecord next = new CertificationRecord(
                id, type, artifactId.trim(), version.trim(), scope, blankToNull(scopeId),
                CertificationStatus.CERTIFIED, certifiedBy.trim(), now,
                null, null, null,
                evidenceSummary, evidenceJson == null ? Map.of() : Map.copyOf(evidenceJson),
                semanticCatalogueVersion, engineVersion, producerVersion,
                now, null,
                existing.map(CertificationRecord::createdAt).orElse(now));
        var event = new InMemoryProductionCertificationLedger.Event(
                UUID.randomUUID(), id, "CERTIFY", from, CertificationStatus.CERTIFIED,
                certifiedBy.trim(), evidenceSummary, evidenceJson, now);
        return ledger.upsert(next, event);
    }

    public CertificationRecord revoke(
            CertifiableArtifactType type,
            String artifactId,
            String version,
            CertificationScopeType scopeType,
            String scopeId,
            String revokedBy,
            String reason) {
        Objects.requireNonNull(revokedBy, "revokedBy");
        CertificationScopeType scope = scopeType == null ? CertificationScopeType.PLATFORM : scopeType;
        CertificationRecord existing = ledger.find(type, artifactId, version, scope, scopeId)
                .orElseThrow(() -> new IllegalArgumentException("No certification record to revoke"));
        Instant now = Instant.now();
        CertificationRecord next = new CertificationRecord(
                existing.certificationId(), existing.artifactType(), existing.artifactId(),
                existing.artifactVersion(), existing.scopeType(), existing.scopeId(),
                CertificationStatus.REVOKED, existing.certifiedBy(), existing.certifiedAt(),
                revokedBy.trim(), now, reason,
                existing.evidenceSummary(), existing.evidenceJson(),
                existing.semanticCatalogueVersion(), existing.engineVersion(), existing.producerVersion(),
                existing.validFrom(), existing.validUntil(), existing.createdAt());
        var event = new InMemoryProductionCertificationLedger.Event(
                UUID.randomUUID(), existing.certificationId(), "REVOKE",
                existing.status(), CertificationStatus.REVOKED, revokedBy.trim(), reason, Map.of(), now);
        return ledger.upsert(next, event);
    }

    public List<Map<String, Object>> listEvidence(
            CertifiableArtifactType type, String artifactId, String version,
            CertificationScopeType scopeType, String scopeId) {
        Optional<CertificationRecord> rec = ledger.find(
                type, artifactId, version,
                scopeType == null ? CertificationScopeType.PLATFORM : scopeType, scopeId);
        if (rec.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> grant = new LinkedHashMap<>(rec.get().toMap());
        grant.put("kind", "CURRENT_RECORD");
        out.add(grant);
        for (var e : ledger.eventsFor(rec.get().certificationId())) {
            out.add(e.toMap());
        }
        return out;
    }

    public Map<String, Object> projectionFor(
            CertifiableArtifactType type, String artifactId, String version,
            CertificationScopeType scopeType, String scopeId) {
        return resolveEffective(type, artifactId, version, scopeType, scopeId)
                .map(CertificationRecord::projection)
                .orElseGet(() -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("certificationStatus", CertificationStatus.UNCERTIFIED.name());
                    m.put("certificationId", null);
                    m.put("certifiedArtifactVersion", version);
                    m.put("artifactType", type == null ? null : type.name());
                    m.put("artifactId", artifactId);
                    return m;
                });
    }

    /**
     * Policy dependency closure: policy certified AND each automatic operand producer/definition certified.
     */
    public Map<String, Object> evaluatePolicyLiveGate(
            String policyId,
            String policyVersion,
            CertificationScopeType scopeType,
            String scopeId,
            List<String> automaticOperandCanonicalIds,
            String scorecardId,
            String scorecardVersion) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> blockers = new ArrayList<>();
        boolean policyOk = isCertifiedForLiveUse(
                CertifiableArtifactType.POLICY_VERSION, policyId, policyVersion, scopeType, scopeId);
        out.put("policyCertified", policyOk);
        if (!policyOk) blockers.add("POLICY_NOT_CERTIFIED:" + policyId + "@" + policyVersion);

        List<Map<String, Object>> operands = new ArrayList<>();
        if (automaticOperandCanonicalIds != null) {
            for (String id : automaticOperandCanonicalIds) {
                boolean ok = isCertifiedForLiveUse(
                        CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, id, "1",
                        scopeType, scopeId)
                        || isCertifiedForLiveUse(
                        CertifiableArtifactType.AUTHORED_CALCULATION_DEFINITION, id, "1",
                        scopeType, scopeId);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("canonicalId", id);
                row.put("certified", ok);
                operands.add(row);
                if (!ok) blockers.add("OPERAND_NOT_CERTIFIED:" + id);
            }
        }
        out.put("operands", operands);

        boolean scoreOk = true;
        if (scorecardId != null && scorecardVersion != null) {
            scoreOk = isCertifiedForLiveUse(
                    CertifiableArtifactType.SCORECARD_VERSION, scorecardId, scorecardVersion,
                    scopeType, scopeId);
            out.put("scorecardCertified", scoreOk);
            if (!scoreOk) blockers.add("SCORECARD_NOT_CERTIFIED:" + scorecardId + "@" + scorecardVersion);
        } else {
            out.put("scorecardCertified", null);
        }

        out.put("blockers", blockers);
        out.put("livePermitted", blockers.isEmpty());
        out.put("operationalBlock", blockers.isEmpty() ? null : "NOT_CERTIFIED");
        out.put("creditReject", false);
        return out;
    }

    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("CERTIFIED_PRODUCER_COUNT", ledger.countByTypeAndStatus(
                CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, CertificationStatus.CERTIFIED));
        m.put("CERTIFIED_DEFINITION_COUNT", ledger.countByTypeAndStatus(
                CertifiableArtifactType.AUTHORED_CALCULATION_DEFINITION, CertificationStatus.CERTIFIED));
        m.put("CERTIFIED_POLICY_COUNT", ledger.countByTypeAndStatus(
                CertifiableArtifactType.POLICY_VERSION, CertificationStatus.CERTIFIED));
        m.put("CERTIFIED_SCORECARD_COUNT", ledger.countByTypeAndStatus(
                CertifiableArtifactType.SCORECARD_VERSION, CertificationStatus.CERTIFIED));
        m.put("LIVE_DATA_AUTO_CERTIFIED", false);
        m.put("authority", AUTHORITY);
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
