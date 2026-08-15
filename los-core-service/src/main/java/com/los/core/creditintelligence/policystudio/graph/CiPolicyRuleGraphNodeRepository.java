package com.los.core.creditintelligence.policystudio.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyRuleGraphNodeRepository extends JpaRepository<CiPolicyRuleGraphNode, UUID> {
    List<CiPolicyRuleGraphNode> findByGraphIdOrderBySortOrderAsc(UUID graphId);
    void deleteByGraphId(UUID graphId);
}
