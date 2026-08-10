package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftDiff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyDraftDiffRepository extends JpaRepository<CiPolicyDraftDiff, UUID> {
    List<CiPolicyDraftDiff> findByTenantIdAndFromPackageIdAndToPackageId(
            UUID tenantId, UUID fromPackageId, UUID toPackageId);
}
