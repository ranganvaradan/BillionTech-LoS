package com.los.core.creditintelligence.gst.repository;

import com.los.core.creditintelligence.gst.domain.CiGstPeriodFinancials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiGstPeriodFinancialsRepository extends JpaRepository<CiGstPeriodFinancials, UUID> {

    Optional<CiGstPeriodFinancials> findByReturnPeriodId(UUID returnPeriodId);

    List<CiGstPeriodFinancials> findByReturnPeriodIdIn(List<UUID> returnPeriodIds);
}
