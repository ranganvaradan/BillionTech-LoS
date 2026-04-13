package com.los.core.repository;

import com.los.core.model.entity.CoLendingPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoLendingPartnerRepository extends JpaRepository<CoLendingPartner, UUID> {
    List<CoLendingPartner> findByActiveTrue();
    Optional<CoLendingPartner> findByPartnerCode(String partnerCode);
}
