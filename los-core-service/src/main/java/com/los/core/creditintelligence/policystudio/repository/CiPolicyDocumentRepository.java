package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyDocumentRepository extends JpaRepository<CiPolicyDocument, UUID> {
    List<CiPolicyDocument> findByTenantIdOrderByUploadedAtDesc(UUID tenantId);
    Optional<CiPolicyDocument> findByTenantIdAndContentHashAndDocumentVersion(UUID tenantId, String contentHash, Integer documentVersion);
}
