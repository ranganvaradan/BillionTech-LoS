package com.los.core.creditintelligence.reconciliation.repository;

import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiReconciliationDefinitionRepository extends JpaRepository<CiReconciliationDefinition, UUID> {

    List<CiReconciliationDefinition> findByStatusOrderByReconciliationCodeAsc(String status);

    Optional<CiReconciliationDefinition> findFirstByReconciliationCodeAndStatusOrderByVersionDesc(
            String reconciliationCode, String status);

    Optional<CiReconciliationDefinition> findByReconciliationCodeAndVersion(String reconciliationCode, String version);
}
