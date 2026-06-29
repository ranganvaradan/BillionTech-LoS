package com.los.core.repository;

import com.los.core.model.entity.CersaiRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CersaiRegistrationRepository extends JpaRepository<CersaiRegistration, UUID> {

    List<CersaiRegistration> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CersaiRegistration> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
