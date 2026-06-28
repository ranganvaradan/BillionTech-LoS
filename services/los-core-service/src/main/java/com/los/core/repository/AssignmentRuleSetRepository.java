package com.los.core.repository;

import com.los.core.model.entity.AssignmentRuleSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentRuleSetRepository extends JpaRepository<AssignmentRuleSet, UUID> {

    List<AssignmentRuleSet> findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
            String borrowerType, String loanProduct);
}
