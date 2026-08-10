package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiItrReturnRepository extends JpaRepository<CiItrReturn, UUID> {

    List<CiItrReturn> findByApplicationIdOrderByAssessmentYearDescCreatedAtDesc(UUID applicationId);

    Optional<CiItrReturn> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiItrReturn> findByTenantIdAndApplicationIdAndIdempotencyKey(
            UUID tenantId, UUID applicationId, String idempotencyKey);

    List<CiItrReturn> findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(UUID applicationId);

    Optional<CiItrReturn> findByApplicationIdAndSubjectScopeAndAssessmentYearAndEffectiveTrue(
            UUID applicationId, String subjectScope, String assessmentYear);

    List<CiItrReturn> findByApplicationIdAndAssessmentYear(UUID applicationId, String assessmentYear);
}
