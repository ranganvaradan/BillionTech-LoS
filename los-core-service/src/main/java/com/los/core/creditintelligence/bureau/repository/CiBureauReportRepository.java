package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiBureauReportRepository extends JpaRepository<CiBureauReport, UUID> {

    Optional<CiBureauReport> findByTenantIdAndApplicationIdAndIdempotencyKey(
            UUID tenantId, UUID applicationId, String idempotencyKey);

    List<CiBureauReport> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiBureauReport> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    /** Unordered load — canonical resolver must not pick newest-by-createdAt. */
    List<CiBureauReport> findByApplicationId(UUID applicationId);
}
