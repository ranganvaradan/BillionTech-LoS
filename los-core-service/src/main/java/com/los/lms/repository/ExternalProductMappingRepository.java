package com.los.lms.repository;

import com.los.lms.entity.ExternalProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExternalProductMappingRepository extends JpaRepository<ExternalProductMapping, UUID> {

    List<ExternalProductMapping> findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
            String losProductCode,
            String externalSystem,
            String status,
            LocalDate asOf);

    List<ExternalProductMapping> findByLosProductCodeAndExternalSystemAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
            String losProductCode,
            String externalSystem,
            LocalDate asOf);

    List<ExternalProductMapping> findByLosProductCodeAndExternalSystemOrderByVersionDesc(
            String losProductCode,
            String externalSystem);

    List<ExternalProductMapping> findByLosProductCodeOrderByExternalSystemAscVersionDesc(String losProductCode);

    /**
     * Active overlap gate:
     * - existing.effective_from <= newEffectiveTo
     * - existing.effective_to >= newEffectiveFrom
     * This ensures there is never more than one ACTIVE/effective mapping
     * overlapping for the same (los_product_code, external_system).
     */
    @Query("""
            SELECT m
            FROM external_product_mapping m
            WHERE m.losProductCode = :losProductCode
              AND m.externalSystem = :externalSystem
              AND m.status = :activeStatus
              AND m.effectiveFrom <= :effectiveTo
              AND m.effectiveTo >= :effectiveFrom
              AND (:excludeId IS NULL OR m.id <> :excludeId)
            ORDER BY m.version DESC
            """)
    List<ExternalProductMapping> findActiveOverlapping(
            @Param("losProductCode") String losProductCode,
            @Param("externalSystem") String externalSystem,
            @Param("activeStatus") String activeStatus,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("excludeId") UUID excludeId);
}

