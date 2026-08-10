package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBureauProductMappingRepository extends JpaRepository<CiBureauProductMapping, UUID> {

    List<CiBureauProductMapping> findByProviderCodeAndMappingVersion(String providerCode, String mappingVersion);
}
