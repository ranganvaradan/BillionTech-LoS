package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiLenderAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CiLenderAliasRepository extends JpaRepository<CiLenderAlias, UUID> {
    List<CiLenderAlias> findByLenderIdentityId(UUID lenderIdentityId);

    @Query("select a from CiLenderAlias a where lower(a.aliasText) = lower(:alias)")
    List<CiLenderAlias> findByAliasTextIgnoreCase(@Param("alias") String alias);
}
