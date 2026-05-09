package com.los.core.repository;

import com.los.core.model.entity.CreditAppraisalMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditAppraisalMemoRepository extends JpaRepository<CreditAppraisalMemo, UUID> {

    Optional<CreditAppraisalMemo> findByApplicationId(UUID applicationId);
}
