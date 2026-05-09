package com.los.core.model.catalog;

import com.los.core.model.enums.BorrowerType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Master borrower × product grid (4 × 8 = 32 segments) for default seeds and policy scope.
 */
public final class StandardCatalog {

    public static final List<String> BORROWER_TYPE_CODES = Arrays.stream(BorrowerType.values())
            .map(Enum::name)
            .toList();

    public static int combinationCount() {
        return BORROWER_TYPE_CODES.size() * StandardLoanProduct.ALL.size();
    }

    public static Stream<Segment> segments() {
        return BORROWER_TYPE_CODES.stream()
                .flatMap(bt -> StandardLoanProduct.ALL.stream().map(lp -> new Segment(bt, lp)));
    }

    public record Segment(String borrowerType, String loanProduct) {
    }

    private StandardCatalog() {
    }
}
