package com.los.core.customercategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerCategoryRepository extends JpaRepository<CustomerCategoryEntity, UUID> {

    List<CustomerCategoryEntity> findAllByOrderByCodeAscVersionNoDesc();

    Optional<CustomerCategoryEntity> findByCodeAndVersionNo(String code, int versionNo);

    Optional<CustomerCategoryEntity> findFirstBySeedSourceRuleSetIdAndStatusOrderByVersionNoDesc(
            UUID seedSourceRuleSetId, ConfigLifecycleStatus status);

    List<CustomerCategoryEntity> findByStatus(ConfigLifecycleStatus status);

    List<CustomerCategoryEntity> findByStatusOrderByCodeAsc(ConfigLifecycleStatus status);
}
