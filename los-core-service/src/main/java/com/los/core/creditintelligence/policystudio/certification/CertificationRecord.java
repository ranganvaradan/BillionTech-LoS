package com.los.core.creditintelligence.policystudio.certification;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable view of a certification ledger row + latest effective status.
 */
public record CertificationRecord(
        UUID certificationId,
        CertifiableArtifactType artifactType,
        String artifactId,
        String artifactVersion,
        CertificationScopeType scopeType,
        String scopeId,
        CertificationStatus status,
        String certifiedBy,
        Instant certifiedAt,
        String revokedBy,
        Instant revokedAt,
        String revocationReason,
        String evidenceSummary,
        Map<String, Object> evidenceJson,
        String semanticCatalogueVersion,
        String engineVersion,
        String producerVersion,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt
) {
    public CertificationRecord {
        if (evidenceJson == null) evidenceJson = Map.of();
    }

    public boolean permitsLiveUse(Instant asOf) {
        if (status != CertificationStatus.CERTIFIED) return false;
        Instant t = asOf == null ? Instant.now() : asOf;
        if (validFrom != null && t.isBefore(validFrom)) return false;
        if (validUntil != null && t.isAfter(validUntil)) return false;
        return true;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("certificationId", certificationId == null ? null : certificationId.toString());
        m.put("artifactType", artifactType == null ? null : artifactType.name());
        m.put("artifactId", artifactId);
        m.put("artifactVersion", artifactVersion);
        m.put("scopeType", scopeType == null ? null : scopeType.name());
        m.put("scopeId", scopeId);
        m.put("status", status == null ? null : status.name());
        m.put("certifiedBy", certifiedBy);
        m.put("certifiedAt", certifiedAt == null ? null : certifiedAt.toString());
        m.put("revokedBy", revokedBy);
        m.put("revokedAt", revokedAt == null ? null : revokedAt.toString());
        m.put("revocationReason", revocationReason);
        m.put("evidenceSummary", evidenceSummary);
        m.put("evidenceJson", evidenceJson);
        m.put("semanticCatalogueVersion", semanticCatalogueVersion);
        m.put("engineVersion", engineVersion);
        m.put("producerVersion", producerVersion);
        m.put("validFrom", validFrom == null ? null : validFrom.toString());
        m.put("validUntil", validUntil == null ? null : validUntil.toString());
        m.put("createdAt", createdAt == null ? null : createdAt.toString());
        m.put("permitsLiveUse", permitsLiveUse(Instant.now()));
        return m;
    }

    public Map<String, Object> projection() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("certificationStatus", status == null ? CertificationStatus.UNCERTIFIED.name() : status.name());
        m.put("certificationId", certificationId == null ? null : certificationId.toString());
        m.put("certifiedArtifactVersion", artifactVersion);
        m.put("artifactType", artifactType == null ? null : artifactType.name());
        m.put("artifactId", artifactId);
        m.put("scopeType", scopeType == null ? null : scopeType.name());
        m.put("scopeId", scopeId);
        return m;
    }
}
