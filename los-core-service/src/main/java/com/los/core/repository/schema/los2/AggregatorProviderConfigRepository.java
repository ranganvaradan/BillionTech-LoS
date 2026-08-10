package com.los.core.repository.schema.los2;

import com.los.core.model.entity.schema.los2.AggregatorProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AggregatorProviderConfigRepository extends JpaRepository<AggregatorProviderConfig, UUID> {

    List<AggregatorProviderConfig> findByProviderNameAndActiveTrue(String providerName);
}
