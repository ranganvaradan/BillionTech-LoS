package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyClauseRepository extends JpaRepository<CiPolicyClause, UUID> {
    List<CiPolicyClause> findByPolicyDocumentIdOrderBySortOrderAsc(UUID policyDocumentId);
}