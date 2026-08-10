package com.los.core.creditintelligence.policystudio.lifecycle.repository;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyApplicabilityRepository extends JpaRepository<CiPolicyApplicability, UUID> {

    List<CiPolicyApplicability> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<CiPolicyApplicability> findByTenantIdAndPolicyDocumentIdAndPolicyVersionLabel(
            UUID tenantId, UUID policyDocumentId, String policyVersionLabel);

    List<CiPolicyApplicability> findByTenantIdAndPolicyDocumentIdOrderByUpdatedAtDesc(
            UUID tenantId, UUID policyDocumentId);

    @Query("""
            SELECT a FROM CiPolicyApplicability a
            WHERE a.tenantId = :tenantId
              AND a.businessStatus IN ('ACTIVE', 'SCHEDULED', 'APPROVED')
            ORDER BY a.effectiveFrom ASC NULLS LAST, a.createdAt ASC
            """)
    List<CiPolicyApplicability> findResolvableByTenant(@Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM CiPolicyApplicability a
            WHERE a.tenantId = :tenantId
              AND a.businessStatus IN ('ACTIVE', 'SCHEDULED', 'APPROVED')
              AND a.id <> :excludeId
              AND a.effectiveFrom IS NOT NULL
              AND a.effectiveFrom <= COALESCE(:until, a.effectiveFrom)
              AND (a.effectiveUntil IS NULL OR a.effectiveUntil >= :from)
            """)
    List<CiPolicyApplicability> findOverlappingForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("excludeId") UUID excludeId,
            @Param("from") LocalDate from,
            @Param("until") LocalDate until);

    long countByTenantIdAndBusinessStatusIn(UUID tenantId, List<String> statuses);
}
