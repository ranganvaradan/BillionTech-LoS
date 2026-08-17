package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauScoringElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBureauScoringElementRepository extends JpaRepository<CiBureauScoringElement, UUID> {

    List<CiBureauScoringElement> findByBureauReportIdOrderBySeqNoAsc(UUID bureauReportId);
}
