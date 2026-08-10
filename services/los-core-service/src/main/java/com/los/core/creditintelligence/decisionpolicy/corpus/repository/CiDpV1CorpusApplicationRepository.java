package com.los.core.creditintelligence.decisionpolicy.corpus.repository;

import com.los.core.creditintelligence.decisionpolicy.corpus.domain.CiDpV1CorpusApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiDpV1CorpusApplicationRepository extends JpaRepository<CiDpV1CorpusApplication, UUID> {
    List<CiDpV1CorpusApplication> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    Optional<CiDpV1CorpusApplication> findByTenantIdAndApplicationToken(UUID tenantId, String token);

    long countByTenantIdAndCountsTowardCertificationTrue(UUID tenantId);

    long countByTenantIdAndUsableTrue(UUID tenantId);

    void deleteByTenantId(UUID tenantId);
}
