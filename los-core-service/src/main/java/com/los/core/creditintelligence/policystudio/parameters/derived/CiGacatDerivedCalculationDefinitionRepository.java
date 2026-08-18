package com.los.core.creditintelligence.policystudio.parameters.derived;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiGacatDerivedCalculationDefinitionRepository
        extends JpaRepository<CiGacatDerivedCalculationDefinition, UUID> {

    List<CiGacatDerivedCalculationDefinition> findByCanonicalParameterIdOrderByVersionNoDesc(
            String canonicalParameterId);

    List<CiGacatDerivedCalculationDefinition> findByCanonicalParameterId(String canonicalParameterId);

    Optional<CiGacatDerivedCalculationDefinition>
            findFirstByCanonicalParameterIdAndTenantIdIsNullAndStatusNotOrderByVersionNoDesc(
                    String canonicalParameterId, String retiredStatus);

    Optional<CiGacatDerivedCalculationDefinition>
            findFirstByCanonicalParameterIdAndTenantIdAndStatusNotOrderByVersionNoDesc(
                    String canonicalParameterId, UUID tenantId, String retiredStatus);
}
