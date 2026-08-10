package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiObligationMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiObligationMatchRepository extends JpaRepository<CiObligationMatch, UUID> {
    List<CiObligationMatch> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<CiObligationMatch> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);
}
