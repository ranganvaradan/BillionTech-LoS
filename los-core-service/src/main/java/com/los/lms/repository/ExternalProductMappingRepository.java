package com.los.lms.repository;

import com.los.lms.entity.ExternalProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExternalProductMappingRepository extends JpaRepository<ExternalProductMapping, UUID> {

    @Query("""
            SELECT m
            FROM ExternalProductMapping m
            WHERE m.losProductCode = :losProductCode
              AND m.externalSystem = :externalSystem
              AND m.bookType = :bookType
              AND m.status = :status
              AND m.effectiveFrom <= :asOf
              AND m.effectiveTo >= :asOf
            ORDER BY m.version DESC
            """)
    List<ExternalProductMapping> findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
            @Param("losProductCode") String losProductCode,
            @Param("externalSystem") String externalSystem,
            @Param("bookType") String bookType,
            @Param("status") String status,
            @Param("asOf") LocalDate asOf);

    @Query("""
            SELECT m
            FROM ExternalProductMapping m
            WHERE m.losProductCode = :losProductCode
              AND m.externalSystem = :externalSystem
              AND m.bookType = :bookType
              AND m.effectiveFrom <= :asOf
              AND m.effectiveTo >= :asOf
            ORDER BY m.version DESC
            """)
    List<ExternalProductMapping> findByLosProductCodeAndExternalSystemAndBookTypeAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
            @Param("losProductCode") String losProductCode,
            @Param("externalSystem") String externalSystem,
            @Param("bookType") String bookType,
            @Param("asOf") LocalDate asOf);

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
            FROM ExternalProductMapping m
            WHERE m.losProductCode = :losProductCode
              AND m.externalSystem = :externalSystem
              AND m.bookType = :bookType
              AND m.status = :activeStatus
              AND m.effectiveFrom <= :effectiveTo
              AND m.effectiveTo >= :effectiveFrom
              AND (:excludeId IS NULL OR m.id <> :excludeId)
            ORDER BY m.version DESC
            """)
    List<ExternalProductMapping> findActiveOverlapping(
            @Param("losProductCode") String losProductCode,
            @Param("externalSystem") String externalSystem,
            @Param("bookType") String bookType,
            @Param("activeStatus") String activeStatus,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("excludeId") UUID excludeId);
}

