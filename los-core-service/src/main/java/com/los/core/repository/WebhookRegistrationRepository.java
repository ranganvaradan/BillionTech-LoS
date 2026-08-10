package com.los.core.repository;

import com.los.core.model.entity.WebhookRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookRegistrationRepository extends JpaRepository<WebhookRegistration, UUID> {
    List<WebhookRegistration> findByEventTypeAndActiveTrue(String eventType);
    List<WebhookRegistration> findByPartnerName(String partnerName);
}
