package com.los.core.repository;

import com.los.core.model.entity.KfsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KfsTemplateRepository extends JpaRepository<KfsTemplate, UUID> {

    Optional<KfsTemplate> findFirstByLoanProductAndActiveTrueOrderByCreatedAtDesc(String loanProduct);

    List<KfsTemplate> findByActiveTrueOrderByLoanProductAscCreatedAtDesc();

    List<KfsTemplate> findAllByOrderByCreatedAtDesc();
}
