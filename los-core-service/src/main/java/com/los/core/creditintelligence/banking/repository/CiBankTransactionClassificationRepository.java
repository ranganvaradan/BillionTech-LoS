package com.los.core.creditintelligence.banking.repository;

import com.los.core.creditintelligence.banking.domain.CiBankTransactionClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBankTransactionClassificationRepository
        extends JpaRepository<CiBankTransactionClassification, UUID> {
    List<CiBankTransactionClassification> findByTransactionId(UUID transactionId);
}
