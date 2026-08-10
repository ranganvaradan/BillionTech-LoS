package com.los.core.creditintelligence.banking.repository;

import com.los.core.creditintelligence.banking.domain.CiBankDuplicateGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBankDuplicateGroupRepository extends JpaRepository<CiBankDuplicateGroup, UUID> {
    List<CiBankDuplicateGroup> findByBankAccountId(UUID bankAccountId);
}
