package com.los.core.creditintelligence.banking.repository;

import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiBankAccountRepository extends JpaRepository<CiBankAccount, UUID> {
    List<CiBankAccount> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiBankAccount> findByTenantIdAndApplicationIdAndIdempotencyKey(
            UUID tenantId, UUID applicationId, String idempotencyKey);

    Optional<CiBankAccount> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);
}
