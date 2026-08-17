package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauReportSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CiBureauReportSummaryRepository extends JpaRepository<CiBureauReportSummary, UUID> {
}
