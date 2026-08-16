package com.los.core.creditintelligence.policystudio.parameters.derived;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiGacatDerivedCalculationProposalRepository
        extends JpaRepository<CiGacatDerivedCalculationProposal, UUID> {

    List<CiGacatDerivedCalculationProposal> findByTargetParameterIdOrderByCreatedAtDesc(String targetParameterId);

    Optional<CiGacatDerivedCalculationProposal>
            findFirstByTargetParameterIdAndProposalStatusInOrderByCreatedAtDesc(
                    String targetParameterId, List<String> statuses);
}
