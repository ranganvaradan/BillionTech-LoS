package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauDuplicateGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBureauDuplicateGroupRepository extends JpaRepository<CiBureauDuplicateGroup, UUID> {

    List<CiBureauDuplicateGroup> findByBureauReportId(UUID bureauReportId);
}
