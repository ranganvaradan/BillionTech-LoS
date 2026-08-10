package com.los.core.creditintelligence.gst.repository;

import com.los.core.creditintelligence.gst.domain.CiGstReturnRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiGstReturnRevisionRepository extends JpaRepository<CiGstReturnRevision, UUID> {

    List<CiGstReturnRevision> findByGstRegistrationId(UUID gstRegistrationId);
}
