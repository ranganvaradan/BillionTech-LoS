package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiPolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyVersionRepository extends JpaRepository<CiPolicyVersion, UUID> {

    List<CiPolicyVersion> findByPolicyPackageIdAndContentHashAndStatusIn(
            UUID policyPackageId, String contentHash, Collection<String> statuses);

    Optional<CiPolicyVersion> findTopByPolicyPackageIdOrderByVersionDesc(UUID policyPackageId);
}
