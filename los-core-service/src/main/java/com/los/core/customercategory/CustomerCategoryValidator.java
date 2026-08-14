package com.los.core.customercategory;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Typed validation for Customer Category / Policy Set configuration.
 */
@Component
@RequiredArgsConstructor
public class CustomerCategoryValidator {

    private final UnderwritingRuleSetRepository ruleSetRepository;
    private final UnderwritingScorecardRepository scorecardRepository;

    public void validateMatchDimensions(
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        validateBorrowerType(borrowerType);
        validateLoanProduct(loanProduct);
        validateIntakeSegment(intakeSegment);
        validateAmountRange(minAmount, maxAmount);
    }

    public void validateBorrowerType(String raw) {
        String v = requireDim(raw, "borrowerType");
        if (MatchWildcard.isAny(v)) {
            return;
        }
        try {
            BorrowerType.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw biz("Invalid borrowerType: " + raw, "INVALID_BORROWER_TYPE",
                    Map.of("borrowerType", raw, "allowed", List.of(BorrowerType.values()) ));
        }
    }

    public void validateLoanProduct(String raw) {
        String v = requireDim(raw, "loanProduct");
        if (MatchWildcard.isAny(v)) {
            return;
        }
        if (v.length() > 80) {
            throw biz("loanProduct too long", "INVALID_LOAN_PRODUCT", Map.of("loanProduct", raw));
        }
    }

    public void validateIntakeSegment(String raw) {
        String v = requireDim(raw, "intakeSegment");
        if (MatchWildcard.isAny(v)) {
            return;
        }
        try {
            IntakeSegment.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw biz("Invalid intakeSegment: " + raw, "INVALID_INTAKE_SEGMENT",
                    Map.of("intakeSegment", raw, "allowed", List.of(IntakeSegment.values())));
        }
    }

    public void validateAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount != null && minAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw biz("minAmount must be >= 0", "INVALID_MIN_AMOUNT", Map.of("minAmount", minAmount));
        }
        if (maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw biz("maxAmount must be >= 0", "INVALID_MAX_AMOUNT", Map.of("maxAmount", maxAmount));
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw biz("minAmount must be <= maxAmount", "INVALID_AMOUNT_RANGE",
                    Map.of("minAmount", minAmount, "maxAmount", maxAmount));
        }
    }

    public UnderwritingRuleSet requireLiveReadyRuleSet(UUID id) {
        if (id == null) {
            throw biz("primaryRuleSetId required", "RULE_SET_REQUIRED", Map.of());
        }
        UnderwritingRuleSet rs = ruleSetRepository.findById(id)
                .orElseThrow(() -> biz("Rule set not found: " + id, "RULE_SET_NOT_FOUND",
                        Map.of("ruleSetId", id.toString())));
        if (!rs.isActive()) {
            throw biz("Referenced rule set is not ACTIVE/live-ready: " + id,
                    "RULE_SET_NOT_LIVE_READY",
                    Map.of("ruleSetId", id.toString(), "active", false));
        }
        return rs;
    }

    public void requireLiveReadyAdditionalRuleSets(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (UUID id : ids) {
            requireLiveReadyRuleSet(id);
        }
    }

    public UnderwritingScorecard requireLiveReadyScorecardIfPresent(UUID id) {
        if (id == null) {
            return null;
        }
        UnderwritingScorecard sc = scorecardRepository.findById(id)
                .orElseThrow(() -> biz("Scorecard not found: " + id, "SCORECARD_NOT_FOUND",
                        Map.of("scorecardId", id.toString())));
        if (!sc.isActive()) {
            throw biz("Referenced scorecard is not ACTIVE/live-ready: " + id,
                    "SCORECARD_NOT_LIVE_READY",
                    Map.of("scorecardId", id.toString(), "active", false, "status", sc.getStatus()));
        }
        return sc;
    }

    public String normalizeBorrowerType(String raw) {
        validateBorrowerType(raw);
        return MatchWildcard.isAny(raw) ? MatchWildcard.ANY : raw.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeLoanProduct(String raw) {
        validateLoanProduct(raw);
        return MatchWildcard.isAny(raw) ? MatchWildcard.ANY : raw.trim();
    }

    public String normalizeIntakeSegment(String raw) {
        validateIntakeSegment(raw);
        return MatchWildcard.isAny(raw) ? MatchWildcard.ANY : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireDim(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw biz(field + " required (use ANY for wildcard)", "MISSING_" + field.toUpperCase(Locale.ROOT),
                    Map.of(field, raw == null ? "null" : raw));
        }
        return raw.trim();
    }

    static BusinessRuleException biz(String message, String reason, Map<String, Object> context) {
        return new BusinessRuleException(message, reason, "FIX_CATEGORY_CONFIG",
                context == null ? Map.of() : new LinkedHashMap<>(context));
    }

    /** For tests / overlap that need enum list without throwing on ANY. */
    public static List<String> knownBorrowerTypes() {
        List<String> out = new ArrayList<>();
        for (BorrowerType t : BorrowerType.values()) {
            out.add(t.name());
        }
        return out;
    }
}
