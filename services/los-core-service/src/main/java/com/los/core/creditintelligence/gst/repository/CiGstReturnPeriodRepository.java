package com.los.core.creditintelligence.gst.repository;

import com.los.core.creditintelligence.gst.domain.CiGstReturnPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiGstReturnPeriodRepository extends JpaRepository<CiGstReturnPeriod, UUID> {

    List<CiGstReturnPeriod> findByGstRegistrationId(UUID gstRegistrationId);

    List<CiGstReturnPeriod> findByGstRegistrationIdAndEffectiveTrue(UUID gstRegistrationId);

    Optional<CiGstReturnPeriod> findByGstRegistrationIdAndReturnTypeAndPeriodYyyyMmAndEffectiveTrue(
            UUID gstRegistrationId, String returnType, String periodYyyyMm);

    List<CiGstReturnPeriod> findByGstRegistrationIdAndReturnTypeAndPeriodYyyyMm(
            UUID gstRegistrationId, String returnType, String periodYyyyMm);
}
