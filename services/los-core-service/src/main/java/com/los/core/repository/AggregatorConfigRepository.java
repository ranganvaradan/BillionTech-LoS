package com.los.core.repository;

import com.los.core.model.entity.AggregatorConfig;
import com.los.core.model.enums.IntegrationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AggregatorConfigRepository extends JpaRepository<AggregatorConfig, UUID> {

    @Query("""
            SELECT a FROM AggregatorConfig a
            WHERE a.integrationType = :type
            AND a.active = true
            AND (a.kycStepType IS NULL OR a.kycStepType = :stepName)
            ORDER BY a.priority DESC
            """)
    List<AggregatorConfig> findActiveKycRoutings(@Param("type") IntegrationCategory type,
                                                    @Param("stepName") String stepName);

    @Query("""
            SELECT a FROM AggregatorConfig a
            WHERE a.integrationType = :type
            AND a.active = true
            ORDER BY a.priority DESC
            """)
    List<AggregatorConfig> findActiveByType(@Param("type") IntegrationCategory type);

    /**
     * eSign provider chain: step-specific row wins over {@code esignStepType = null} global rows
     * for the same provider priority order.
     */
    @Query("""
            SELECT a FROM AggregatorConfig a
            WHERE a.integrationType = :type
            AND a.active = true
            AND (a.esignStepType IS NULL OR a.esignStepType = :esignStep)
            ORDER BY a.priority DESC
            """)
    List<AggregatorConfig> findActiveEsignRoutings(@Param("type") IntegrationCategory type,
                                                    @Param("esignStep") String esignStep);
}
