package com.los.core.repository;

import com.los.core.model.entity.UnderwritingScorecard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnderwritingScorecardRepository extends JpaRepository<UnderwritingScorecard, UUID> {

    List<UnderwritingScorecard> findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
            String borrowerType, String loanProduct);
}
