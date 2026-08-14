package com.los.core.customercategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicySetRepository extends JpaRepository<PolicySetEntity, UUID> {

    List<PolicySetEntity> findAllByOrderByCodeAscVersionNoDesc();

    Optional<PolicySetEntity> findByCodeAndVersionNo(String code, int versionNo);

    Optional<PolicySetEntity> findFirstByCodeOrderByVersionNoDesc(String code);

    Optional<PolicySetEntity> findFirstBySeedSourceRuleSetIdAndStatusOrderByVersionNoDesc(
            UUID seedSourceRuleSetId, ConfigLifecycleStatus status);

    List<PolicySetEntity> findByStatus(ConfigLifecycleStatus status);

    List<PolicySetEntity> findByCodeOrderByVersionNoDesc(String code);
}
