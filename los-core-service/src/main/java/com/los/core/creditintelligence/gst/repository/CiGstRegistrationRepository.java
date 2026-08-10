package com.los.core.creditintelligence.gst.repository;

import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiGstRegistrationRepository extends JpaRepository<CiGstRegistration, UUID> {

    List<CiGstRegistration> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiGstRegistration> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiGstRegistration> findByTenantIdAndApplicationIdAndIdempotencyKey(
            UUID tenantId, UUID applicationId, String idempotencyKey);

    List<CiGstRegistration> findByApplicationIdAndGstin(UUID applicationId, String gstin);
}
