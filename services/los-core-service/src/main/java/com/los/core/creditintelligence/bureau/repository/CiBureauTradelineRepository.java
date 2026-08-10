package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBureauTradelineRepository extends JpaRepository<CiBureauTradeline, UUID> {

    List<CiBureauTradeline> findByBureauReportId(UUID bureauReportId);
}
