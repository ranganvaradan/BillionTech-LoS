package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiForm26AsEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiForm26AsEntryRepository extends JpaRepository<CiForm26AsEntry, UUID> {

    List<CiForm26AsEntry> findByForm26asSummaryId(UUID form26asSummaryId);
}
