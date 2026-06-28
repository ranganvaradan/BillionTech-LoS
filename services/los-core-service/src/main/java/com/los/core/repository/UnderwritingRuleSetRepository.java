package com.los.core.repository;

import com.los.core.model.entity.UnderwritingRuleSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnderwritingRuleSetRepository extends JpaRepository<UnderwritingRuleSet, UUID> {

    List<UnderwritingRuleSet> findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
            String borrowerType, String loanProduct);
}
