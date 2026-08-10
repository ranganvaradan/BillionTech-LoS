package com.los.core.creditintelligence.banking.repository;

import com.los.core.creditintelligence.banking.domain.CiBankRecurringObligation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBankRecurringObligationRepository
        extends JpaRepository<CiBankRecurringObligation, UUID> {
    List<CiBankRecurringObligation> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
