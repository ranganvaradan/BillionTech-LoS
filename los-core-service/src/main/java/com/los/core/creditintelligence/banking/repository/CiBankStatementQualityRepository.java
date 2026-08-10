package com.los.core.creditintelligence.banking.repository;

import com.los.core.creditintelligence.banking.domain.CiBankStatementQuality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiBankStatementQualityRepository extends JpaRepository<CiBankStatementQuality, UUID> {
    List<CiBankStatementQuality> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiBankStatementQuality> findFirstByBankAccountIdOrderByCreatedAtDesc(UUID bankAccountId);
}
