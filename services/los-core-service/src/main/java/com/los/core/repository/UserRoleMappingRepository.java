package com.los.core.repository;

import com.los.core.model.entity.UserRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleMappingRepository extends JpaRepository<UserRoleMapping, UUID> {

    List<UserRoleMapping> findByActiveIsTrueAndLosRoleOrderByPriorityDesc(String losRole);
}
