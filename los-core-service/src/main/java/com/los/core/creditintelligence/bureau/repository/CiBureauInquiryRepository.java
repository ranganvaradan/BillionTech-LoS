package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBureauInquiryRepository extends JpaRepository<CiBureauInquiry, UUID> {

    List<CiBureauInquiry> findByBureauReportId(UUID bureauReportId);
}
