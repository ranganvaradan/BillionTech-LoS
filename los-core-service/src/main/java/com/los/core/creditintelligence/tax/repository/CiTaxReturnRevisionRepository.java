package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiTaxReturnRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiTaxReturnRevisionRepository extends JpaRepository<CiTaxReturnRevision, UUID> {

    List<CiTaxReturnRevision> findByApplicationIdAndAssessmentYear(UUID applicationId, String assessmentYear);

    List<CiTaxReturnRevision> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
