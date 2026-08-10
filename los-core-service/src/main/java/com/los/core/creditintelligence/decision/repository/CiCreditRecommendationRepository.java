package com.los.core.creditintelligence.decision.repository;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiCreditRecommendationRepository extends JpaRepository<CiCreditRecommendation, UUID> {

    List<CiCreditRecommendation> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);

    List<CiCreditRecommendation> findByApplicationId(UUID applicationId);
}
