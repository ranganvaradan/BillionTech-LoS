package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.graph.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyRuleGraphRepository extends JpaRepository<CiPolicyRuleGraph, UUID> {
    Optional<CiPolicyRuleGraph> findByPolicyDocumentIdAndDocumentVersion(UUID policyDocumentId, int documentVersion);
    List<CiPolicyRuleGraph> findByPolicyDocumentIdOrderByDocumentVersionDesc(UUID policyDocumentId);
    Optional<CiPolicyRuleGraph> findFirstByPolicyDocumentIdOrderByDocumentVersionDesc(UUID policyDocumentId);
}
