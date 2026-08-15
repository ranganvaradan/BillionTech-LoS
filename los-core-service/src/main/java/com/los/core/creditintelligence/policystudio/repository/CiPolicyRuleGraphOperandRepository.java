package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.graph.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyRuleGraphOperandRepository extends JpaRepository<CiPolicyRuleGraphOperand, UUID> {
    List<CiPolicyRuleGraphOperand> findByGraphId(UUID graphId);
    List<CiPolicyRuleGraphOperand> findByNodeId(UUID nodeId);
    void deleteByGraphId(UUID graphId);
    long countByGraphIdAndResolutionStatus(UUID graphId, String resolutionStatus);
}
