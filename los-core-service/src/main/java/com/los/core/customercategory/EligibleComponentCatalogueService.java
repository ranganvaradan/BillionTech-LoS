package com.los.core.customercategory;

import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.customercategory.CustomerCategoryDtos.EligibleRuleSetView;
import com.los.core.customercategory.CustomerCategoryDtos.EligibleScorecardView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Catalogue of lender-selectable ACTIVE executables for Policy Set composition.
 * Does not auto-select. Not wired to live routing.
 */
@Service
@RequiredArgsConstructor
public class EligibleComponentCatalogueService {

    private final UnderwritingRuleSetRepository ruleSetRepository;
    private final UnderwritingScorecardRepository scorecardRepository;

    @Transactional(readOnly = true)
    public List<EligibleRuleSetView> eligibleRuleSets(String borrowerType, String loanProduct, BigDecimal amount) {
        return ruleSetRepository.findAll().stream()
                .filter(UnderwritingRuleSet::isActive)
                .filter(rs -> matchesBorrower(rs.getBorrowerType(), borrowerType))
                .filter(rs -> matchesProduct(rs.getLoanProduct(), loanProduct))
                .filter(rs -> amountInRange(amount, rs.getMinAmount(), rs.getMaxAmount()))
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .map(rs -> new EligibleRuleSetView(
                        rs.getId(), rs.getName(), rs.getBorrowerType(), rs.getLoanProduct(),
                        rs.getMinAmount(), rs.getMaxAmount(), rs.getPriority(), rs.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EligibleScorecardView> eligibleScorecards(String borrowerType, String loanProduct, BigDecimal amount) {
        return scorecardRepository.findAll().stream()
                .filter(sc -> sc.isExecutionActive() && "ACTIVE".equalsIgnoreCase(sc.getStatus()))
                .filter(sc -> matchesBorrower(sc.getBorrowerType(), borrowerType))
                .filter(sc -> matchesProduct(sc.getLoanProduct(), loanProduct))
                .filter(sc -> amountInRange(amount, sc.getMinAmount(), sc.getMaxAmount()))
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .map(sc -> new EligibleScorecardView(
                        sc.getId(), sc.getName(), sc.getBorrowerType(), sc.getLoanProduct(),
                        sc.getMinAmount(), sc.getMaxAmount(), sc.getPriority(),
                        sc.getStatus(), sc.isActive()))
                .toList();
    }

    private static boolean matchesBorrower(String row, String filter) {
        if (filter == null || filter.isBlank() || MatchWildcard.isAny(filter)) {
            return true;
        }
        return row != null && row.equalsIgnoreCase(filter.trim());
    }

    private static boolean matchesProduct(String row, String filter) {
        if (filter == null || filter.isBlank() || MatchWildcard.isAny(filter)) {
            return true;
        }
        return row != null && row.equals(filter.trim());
    }

    private static boolean amountInRange(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null) {
            return true;
        }
        if (min != null && amount.compareTo(min) < 0) {
            return false;
        }
        if (max != null && amount.compareTo(max) > 0) {
            return false;
        }
        return true;
    }
}
