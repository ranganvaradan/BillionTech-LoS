package com.los.core.repository;

import com.los.core.model.entity.AaConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AaConsentRepository extends JpaRepository<AaConsent, UUID> {

    List<AaConsent> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<AaConsent> findByConsentHandle(String consentHandle);

    Optional<AaConsent> findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(UUID applicationId, String status);

    List<AaConsent> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
