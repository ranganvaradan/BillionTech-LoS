package com.los.core.creditintelligence.core.repository;

import com.los.core.creditintelligence.core.domain.CiEvidenceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiEvidenceGroupRepository extends JpaRepository<CiEvidenceGroup, UUID> {

    List<CiEvidenceGroup> findByApplicationIdAndGroupType(UUID applicationId, String groupType);
}
