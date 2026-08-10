package com.los.core.payment.repository;

import com.los.core.payment.model.LosPgSettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LosPgSettlementBatchRepository extends JpaRepository<LosPgSettlementBatch, UUID> {
}
