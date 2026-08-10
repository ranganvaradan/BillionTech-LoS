package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyReviewRepository extends JpaRepository<CiPolicyReview, UUID> {
    List<CiPolicyReview> findByPolicyDocumentIdOrderByCreatedAtDesc(UUID policyDocumentId);
}