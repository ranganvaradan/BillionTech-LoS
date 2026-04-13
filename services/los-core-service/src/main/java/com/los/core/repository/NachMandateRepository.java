package com.los.core.repository;

import com.los.core.model.entity.NachMandate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NachMandateRepository extends JpaRepository<NachMandate, UUID> {

    List<NachMandate> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<NachMandate> findByMandateReference(String mandateReference);

    Optional<NachMandate> findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(UUID applicationId, String status);
}
