package com.los.core.customercategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic overlap detection. WARNING only — never blocks activation by itself.
 */
public final class CustomerCategoryOverlapDetector {

    private CustomerCategoryOverlapDetector() {}

    public record CategoryCriteria(
            String idOrCode,
            String name,
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {}

    public record OverlapWarning(
            String leftIdOrCode,
            String rightIdOrCode,
            String leftName,
            String rightName,
            List<String> reasons,
            Map<String, Object> evidence
    ) {}

    public static List<OverlapWarning> findOverlaps(List<CategoryCriteria> categories) {
        List<OverlapWarning> out = new ArrayList<>();
        if (categories == null || categories.size() < 2) {
            return out;
        }
        for (int i = 0; i < categories.size(); i++) {
            for (int j = i + 1; j < categories.size(); j++) {
                OverlapWarning w = overlapIfAny(categories.get(i), categories.get(j));
                if (w != null) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    public static OverlapWarning overlapIfAny(CategoryCriteria a, CategoryCriteria b) {
        List<String> reasons = new ArrayList<>();
        Map<String, Object> evidence = new LinkedHashMap<>();

        if (!dimCompatible(a.borrowerType(), b.borrowerType())) {
            return null;
        }
        reasons.add(dimReason("borrowerType", a.borrowerType(), b.borrowerType()));
        evidence.put("borrowerType", Map.of("left", a.borrowerType(), "right", b.borrowerType()));

        if (!dimCompatible(a.loanProduct(), b.loanProduct())) {
            return null;
        }
        reasons.add(dimReason("loanProduct", a.loanProduct(), b.loanProduct()));
        evidence.put("loanProduct", Map.of("left", a.loanProduct(), "right", b.loanProduct()));

        if (!dimCompatible(a.intakeSegment(), b.intakeSegment())) {
            return null;
        }
        reasons.add(dimReason("intakeSegment", a.intakeSegment(), b.intakeSegment()));
        evidence.put("intakeSegment", Map.of("left", a.intakeSegment(), "right", b.intakeSegment()));

        if (!amountsOverlap(a.minAmount(), a.maxAmount(), b.minAmount(), b.maxAmount())) {
            return null;
        }
        reasons.add("amount ranges intersect (inclusive; null = unbounded)");
        evidence.put("amount", Map.of(
                "leftMin", Objects.toString(a.minAmount(), "UNBOUNDED"),
                "leftMax", Objects.toString(a.maxAmount(), "UNBOUNDED"),
                "rightMin", Objects.toString(b.minAmount(), "UNBOUNDED"),
                "rightMax", Objects.toString(b.maxAmount(), "UNBOUNDED")));

        return new OverlapWarning(a.idOrCode(), b.idOrCode(), a.name(), b.name(), reasons, evidence);
    }

    static boolean dimCompatible(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        if (MatchWildcard.isAny(left) || MatchWildcard.isAny(right)) {
            return true;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    /**
     * Inclusive ranges; null bound = unbounded.
     */
    static boolean amountsOverlap(
            BigDecimal minA, BigDecimal maxA,
            BigDecimal minB, BigDecimal maxB) {
        BigDecimal low = maxBound(minA, minB);   // max of lower bounds
        BigDecimal high = minBound(maxA, maxB);  // min of upper bounds
        if (low == null || high == null) {
            // at least one side fully unbounded in that direction → always intersect if other checks pass
            // low null means both mins null; high null means both maxes null → still overlap
            return true;
        }
        // if one min null: low = other min; if one max null: high = other max — handled by maxBound/minBound
        return low.compareTo(high) <= 0;
    }

    private static BigDecimal maxBound(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null; // -inf
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static BigDecimal minBound(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null; // +inf
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String dimReason(String dim, String left, String right) {
        if (MatchWildcard.isAny(left) || MatchWildcard.isAny(right)) {
            return dim + " compatible via ANY";
        }
        return dim + " exact match (" + left + ")";
    }

    /** Amount in inclusive range (null app amount handled by caller). */
    public static boolean amountInRange(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null) {
            return min == null && max == null;
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
