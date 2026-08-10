package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiAisInformation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiAisInformationRepository extends JpaRepository<CiAisInformation, UUID> {

    List<CiAisInformation> findByAisSummaryId(UUID aisSummaryId);
}
